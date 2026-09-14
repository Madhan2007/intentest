# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""`opzhubctl migrate` — kernel + each present module's db/*.sql, idempotent,
create/alter only (doc 06 §7, doc 19). db.type=memory has nothing to apply."""
from __future__ import annotations

import glob
import hashlib
import os

from opzhub_scripts import config, logging as opzlog


def _module_sql_files() -> list[str]:
    files: list[str] = []
    for prefix in ("", "../../", "../"):
        found = sorted(glob.glob(os.path.join(prefix, "modules", "*", "db", "*.sql")))
        if found:
            files = found
            break
    return files


async def _apply_postgres(settings, files: list[str]) -> int:
    import asyncpg  # lazy import — only needed when db.type=postgres

    pg = settings.get("db.postgres", {}) or {}
    conn = await asyncpg.connect(
        host=pg.get("host", "postgres"),
        port=int(pg.get("port", 5432)),
        database=pg.get("database", "erp"),
        user=pg.get("user", "erp_app"),
        password=pg.get("password", ""),
    )
    try:
        await conn.execute(
            """
            CREATE TABLE IF NOT EXISTS kernel_schema_migrations (
                filename   TEXT PRIMARY KEY,
                checksum   TEXT NOT NULL,
                applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
            """
        )
        applied = 0
        for path in files:
            filename = os.path.basename(path)
            with open(path, "r", encoding="utf-8") as f:
                sql = f.read()
            checksum = hashlib.sha256(sql.encode("utf-8")).hexdigest()
            row = await conn.fetchrow(
                "SELECT checksum FROM kernel_schema_migrations WHERE filename = $1", filename
            )
            if row is not None:
                if row["checksum"] != checksum:
                    raise RuntimeError(f"checksum changed for already-applied migration: {filename}")
                continue
            await conn.execute(sql)
            await conn.execute(
                "INSERT INTO kernel_schema_migrations (filename, checksum) VALUES ($1, $2)",
                filename, checksum,
            )
            applied += 1
        return applied
    finally:
        await conn.close()


def run(args) -> int:
    settings = config.load()
    files = _module_sql_files()
    if settings.db_type == "memory":
        print("migrate: db.type=memory — nothing to apply")
        opzlog.log("migrate", db_type="memory", applied=0)
        return 0
    if settings.db_type != "postgres":
        print(f"migrate: unsupported db.type={settings.db_type}")
        return 4
    import asyncio

    try:
        applied = asyncio.run(_apply_postgres(settings, files))
    except Exception as e:
        print(f"migrate: FAILED — {e}")
        opzlog.log("migrate", ok=False, error=str(e))
        return 4
    print(f"migrate: applied {applied} new migration(s) out of {len(files)} found")
    opzlog.log("migrate", ok=True, applied=applied, total=len(files))
    return 0
