# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""db.type=postgres — production DataServer (doc 07 §4.1). asyncpg is
imported lazily so importing this module does not require the driver to be
installed when db.type=memory."""
from __future__ import annotations

from typing import Any

# Named SQL commands loaded from modules/*/db/commands/*.sql, mirroring the
# Java CommandCatalog (doc 07 §3.1). Kept minimal for this pass — module
# plugins register their own command file discovery via glob at connect time.
import glob
import os


class PostgresDataServer:
    def __init__(self, postgres_config: dict[str, Any]) -> None:
        self._config = postgres_config
        self._pool = None
        self._commands = self._load_commands()

    def _load_commands(self) -> dict[str, str]:
        commands: dict[str, str] = {}
        for prefix in ("", "../../", "../"):
            pattern = os.path.join(prefix, "modules", "*", "db", "commands", "*.sql")
            for path in glob.glob(pattern):
                name = os.path.splitext(os.path.basename(path))[0]
                with open(path, "r", encoding="utf-8") as f:
                    commands[name] = f.read()
            if commands:
                break
        return commands

    async def _ensure_pool(self):
        if self._pool is None:
            import asyncpg  # lazy import (doc 05 §4)
            self._pool = await asyncpg.create_pool(
                host=self._config.get("host", "postgres"),
                port=int(self._config.get("port", 5432)),
                database=self._config.get("database", "erp"),
                user=self._config.get("user", "ai_app"),
                password=self._config.get("password", ""),
                min_size=int(self._config.get("pool_min", 2)),
                max_size=int(self._config.get("pool_max", 8)),
            )
        return self._pool

    async def ping(self) -> bool:
        try:
            pool = await self._ensure_pool()
            async with pool.acquire() as conn:
                await conn.fetchval("SELECT 1")
            return True
        except Exception:
            return False

    def _sql_for(self, statement: str) -> str:
        sql = self._commands.get(statement)
        if sql is None:
            raise KeyError(f"No SQL command registered for: {statement}")
        return sql

    @staticmethod
    def _to_positional(sql: str, params: dict[str, Any]) -> tuple[str, list[Any]]:
        # Named :param -> asyncpg positional $1, $2, ... (kept simple; a real
        # implementation should use a proper SQL tokenizer).
        ordered: list[Any] = []
        result = sql
        for i, (key, value) in enumerate(params.items(), start=1):
            result = result.replace(f":{key}", f"${i}")
            ordered.append(value)
        return result, ordered

    async def execute(self, statement: str, params: dict[str, Any]) -> int:
        pool = await self._ensure_pool()
        sql, args = self._to_positional(self._sql_for(statement), params)
        async with pool.acquire() as conn:
            result = await conn.execute(sql, *args)
            return int(result.split()[-1]) if result else 0

    async def query(self, statement: str, params: dict[str, Any]) -> list[dict[str, Any]]:
        pool = await self._ensure_pool()
        sql, args = self._to_positional(self._sql_for(statement), params)
        async with pool.acquire() as conn:
            rows = await conn.fetch(sql, *args)
            return [dict(row) for row in rows]

    async def query_one(self, statement: str, params: dict[str, Any]) -> dict[str, Any] | None:
        rows = await self.query(statement, params)
        return rows[0] if rows else None
