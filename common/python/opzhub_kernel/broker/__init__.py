# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Kernel async job port (doc 07 §7). broker.type=valkey (Streams) is
reserved for a later drop (doc 11); this pass ships `memory` only."""
from __future__ import annotations

import asyncio
from typing import Any, Awaitable, Callable


class MemoryBrokerServer:
    def __init__(self) -> None:
        self._queues: dict[str, asyncio.Queue] = {}

    def _queue_for(self, name: str) -> asyncio.Queue:
        return self._queues.setdefault(name, asyncio.Queue())

    async def publish(self, queue: str, command: dict[str, Any]) -> None:
        await self._queue_for(queue).put(command)

    async def consume(self, queue: str, handler: Callable[[dict[str, Any]], Awaitable[None]]) -> None:
        q = self._queue_for(queue)
        while True:
            command = await q.get()
            await handler(command)

    async def ping(self) -> bool:
        return True


def build_broker_client(settings) -> Any:
    broker_type = settings.broker_type
    if broker_type == "memory":
        return MemoryBrokerServer()
    if broker_type == "kafka":
        raise ValueError("broker.type=kafka is reserved and not implemented (doc 11)")
    raise ValueError(f"Unknown or not-yet-implemented broker.type: {broker_type}")
