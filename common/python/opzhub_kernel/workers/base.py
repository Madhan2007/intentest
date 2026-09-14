# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Async worker bootstrap for the combined Python core runtime.

When no module registers a worker, the worker process remains idle until the
container receives its shutdown signal.
"""
from __future__ import annotations

import asyncio
from typing import Awaitable, Callable

_REGISTRY: dict[str, Callable[..., Awaitable[None]]] = {}


class WorkerRegistry:
    def worker(self, queue: str, handler: Callable[..., Awaitable[None]]) -> None:
        _REGISTRY[queue] = handler


async def run_all_registered_workers() -> None:
    if not _REGISTRY:
        await asyncio.Event().wait()
    await asyncio.gather(*(handler() for handler in _REGISTRY.values()))
