# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""cache.type factory (doc 07 §8)."""
from __future__ import annotations

from typing import Any

from opzhub_kernel.cache.servers.memory import MemoryCacheServer
from opzhub_kernel.cache.servers.valkey import ValkeyCacheServer


def build_cache_client(settings) -> Any:
    cache_type = settings.cache_type
    if cache_type == "valkey":
        return ValkeyCacheServer(settings.get("cache.valkey", {}) or {})
    if cache_type == "memory":
        return MemoryCacheServer()
    raise ValueError(f"Unknown cache.type: {cache_type}")
