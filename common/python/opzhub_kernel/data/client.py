# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Kernel data port (doc 07 §3) — Python mirror of Java DataClient. Feature
modules depend on this Protocol only; `db.type` selects the server."""
from __future__ import annotations

from typing import Any, Protocol


class DataClientError(Exception):
    def __init__(self, kind: str, message: str) -> None:
        super().__init__(message)
        self.kind = kind


class DataClient(Protocol):
    async def ping(self) -> bool: ...

    async def execute(self, statement: str, params: dict[str, Any]) -> int: ...

    async def query(self, statement: str, params: dict[str, Any]) -> list[dict[str, Any]]: ...

    async def query_one(self, statement: str, params: dict[str, Any]) -> dict[str, Any] | None: ...
