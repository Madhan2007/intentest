# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""cache.type=memory — single process only (doc 07 §6)."""
from __future__ import annotations

import asyncio
import fnmatch
import time
from typing import Any, Awaitable, Callable


class MemoryCacheServer:
    def __init__(self) -> None:
        self._store: dict[str, tuple[str, float | None]] = {}
        self._subscribers: list[tuple[str, Callable[[str], Awaitable[None]]]] = []
        self._lock = asyncio.Lock()

    async def get(self, key: str) -> str | None:
        entry = self._store.get(key)
        if entry is None:
            return None
        value, expires_at = entry
        if expires_at is not None and time.time() > expires_at:
            self._store.pop(key, None)
            return None
        return value

    async def set(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        expires_at = time.time() + ttl_seconds if ttl_seconds else None
        self._store[key] = (value, expires_at)

    async def delete(self, key: str) -> None:
        self._store.pop(key, None)

    async def set_if_absent(self, key: str, value: str, ttl_seconds: int | None = None) -> bool:
        async with self._lock:
            if await self.get(key) is not None:
                return False
            await self.set(key, value, ttl_seconds)
            return True

    async def publish(self, topic: str, payload: str) -> None:
        for pattern, handler in self._subscribers:
            if fnmatch.fnmatch(topic, pattern):
                await handler(payload)

    async def subscribe(self, topic_pattern: str, handler: Callable[[str], Awaitable[None]]) -> None:
        self._subscribers.append((topic_pattern, handler))

    async def ping(self) -> bool:
        return True
