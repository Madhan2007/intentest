# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""db.type factory (doc 07 §8) — unknown type is a fatal boot error."""
from __future__ import annotations

from typing import Any

from opzhub_kernel.data.servers.memory import MemoryDataServer
from opzhub_kernel.data.servers.postgres import PostgresDataServer


def build_data_client(settings) -> Any:
    db_type = settings.db_type
    if db_type == "postgres":
        return PostgresDataServer(settings.get("db.postgres", {}) or {})
    if db_type == "memory":
        return MemoryDataServer()
    raise ValueError(f"Unknown db.type: {db_type}")
