# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""cache.type=valkey — production CacheServer (doc 07 §6.1). `redis` (Valkey
protocol compatible) is imported lazily so this module is safe to import
without the dependency installed when cache.type=memory."""
from __future__ import annotations

from typing import Any, Awaitable, Callable


class ValkeyCacheServer:
    def __init__(self, valkey_config: dict[str, Any]) -> None:
        self._config = valkey_config
        self._client = None
        self._prefix = valkey_config.get("key_prefix", "erp") + ":"

    def _ensure_client(self):
        if self._client is None:
            import redis.asyncio as redis  # lazy import (doc 05 §4)
            self._client = redis.Redis(
                host=self._config.get("host", "valkey"),
                port=int(self._config.get("port", 6380)),
                password=self._config.get("password") or None,
                ssl=bool(self._config.get("tls", True)),
                db=int(self._config.get("db", 0)),
                decode_responses=True,
            )
        return self._client

    def _k(self, key: str) -> str:
        return self._prefix + key

    async def get(self, key: str) -> str | None:
        return await self._ensure_client().get(self._k(key))

    async def set(self, key: str, value: str, ttl_seconds: int | None = None) -> None:
        await self._ensure_client().set(self._k(key), value, ex=ttl_seconds)

    async def delete(self, key: str) -> None:
        await self._ensure_client().delete(self._k(key))

    async def set_if_absent(self, key: str, value: str, ttl_seconds: int | None = None) -> bool:
        return bool(await self._ensure_client().set(self._k(key), value, ex=ttl_seconds, nx=True))

    async def publish(self, topic: str, payload: str) -> None:
        await self._ensure_client().publish(self._k(topic), payload)

    async def subscribe(self, topic_pattern: str, handler: Callable[[str], Awaitable[None]]) -> None:
        pubsub = self._ensure_client().pubsub()
        await pubsub.psubscribe(self._k(topic_pattern))
        async for message in pubsub.listen():
            if message["type"] == "pmessage":
                await handler(message["data"])

    async def ping(self) -> bool:
        return bool(await self._ensure_client().ping())
