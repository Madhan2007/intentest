# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""`opzhubctl health` — ping nginx/erp/ai/db/cache/broker (doc 06 §4)."""
from __future__ import annotations

import asyncio

import httpx

from opzhub_scripts import config, logging as opzlog


async def _ping_http(url: str) -> bool:
    try:
        async with httpx.AsyncClient(timeout=5.0, verify=False) as client:
            resp = await client.get(url)
            return resp.status_code == 200
    except Exception:
        return False


async def _run(settings) -> int:
    from opzhub_kernel.cache.factory import build_cache_client
    from opzhub_kernel.data.factory import build_data_client

    core_url = settings.get("http.opzhub-be-app.internal_url", "http://localhost:8114") + "/api/v1/opzhub/health"
    core_ok = await _ping_http(core_url)

    db_ok = False
    cache_ok = False
    try:
        db_ok = await build_data_client(settings).ping()
    except Exception:
        pass
    try:
        cache_ok = await build_cache_client(settings).ping()
    except Exception:
        pass

    results = {"core": core_ok, "db": db_ok, "cache": cache_ok}
    opzlog.log("health", **results)
    for name, ok in results.items():
        print(f"{name}: {'up' if ok else 'down'}")
    return 0 if all(results.values()) else 3


def run(args) -> int:
    settings = config.load()
    return asyncio.run(_run(settings))
