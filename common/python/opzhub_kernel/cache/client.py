# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Kernel cache/session/pub-sub port (doc 07 §5) — Python mirror of Java CacheClient."""
from __future__ import annotations

from typing import Any, Awaitable, Callable, Protocol


class CacheClient(Protocol):
    async def get(self, key: str) -> str | None: ...

    async def set(self, key: str, value: str, ttl_seconds: int | None = None) -> None: ...

    async def delete(self, key: str) -> None: ...

    async def set_if_absent(self, key: str, value: str, ttl_seconds: int | None = None) -> bool: ...

    async def publish(self, topic: str, payload: str) -> None: ...

    async def subscribe(self, topic_pattern: str, handler: Callable[[str], Awaitable[None]]) -> None: ...

    async def ping(self) -> bool: ...
