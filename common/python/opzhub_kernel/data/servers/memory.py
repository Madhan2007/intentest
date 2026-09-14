# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""db.type=memory — tests / kernel-only demos (doc 07 §4.2)."""
from __future__ import annotations

from typing import Any, Callable


class MemoryDataServer:
    def __init__(self) -> None:
        self._queries: dict[str, Callable[[dict[str, Any]], list[dict[str, Any]]]] = {}
        self._executes: dict[str, Callable[[dict[str, Any]], int]] = {}

    def register_query(self, name: str, handler: Callable[[dict[str, Any]], list[dict[str, Any]]]) -> None:
        self._queries[name] = handler

    def register_execute(self, name: str, handler: Callable[[dict[str, Any]], int]) -> None:
        self._executes[name] = handler

    async def ping(self) -> bool:
        return True

    async def execute(self, statement: str, params: dict[str, Any]) -> int:
        handler = self._executes.get(statement)
        if handler is None:
            raise KeyError(f"No memory execute handler registered for: {statement}")
        return handler(params)

    async def query(self, statement: str, params: dict[str, Any]) -> list[dict[str, Any]]:
        handler = self._queries.get(statement)
        if handler is None:
            raise KeyError(f"No memory query handler registered for: {statement}")
        return handler(params)

    async def query_one(self, statement: str, params: dict[str, Any]) -> dict[str, Any] | None:
        rows = await self.query(statement, params)
        return rows[0] if rows else None
