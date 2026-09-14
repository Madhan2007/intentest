# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""FastAPI factory (doc 05 §4). Mounts kernel routers only; module routers
are added by discover_modules() when a Python-runtime module is packed."""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI

from opzhub_kernel.broker import build_broker_client
from opzhub_kernel.cache.factory import build_cache_client
from opzhub_kernel.data.factory import build_data_client
from opzhub_kernel.discover import discover_modules
from opzhub_kernel.settings import Settings
from opzhub_kernel.workers.base import WorkerRegistry


def create_app() -> FastAPI:
    settings = Settings()
    data_client = build_data_client(settings)
    cache_client = build_cache_client(settings)
    broker_client = build_broker_client(settings)
    registry = WorkerRegistry()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.settings = settings
        app.state.data = data_client
        app.state.cache = cache_client
        app.state.broker = broker_client
        app.state.loaded_modules = discover_modules(app, registry, settings)
        yield

    app = FastAPI(title="ManageMyOpz AI Engine", version=settings.get("kernel.api_version", "2026.1"),
                  lifespan=lifespan)

    @app.get("/api/v1/ai/health")
    async def health() -> dict:
        return {
            "ok": True,
            "data": {
                "status": "ok",
                "db": "up" if await data_client.ping() else "down",
                "cache": "up" if await cache_client.ping() else "down",
            },
            "error": None,
            "correlation_id": "",
        }

    @app.get("/api/v1/ai/meta/modules")
    async def meta_modules() -> dict:
        return {"ok": True, "data": {"modules": getattr(app.state, "loaded_modules", [])}, "error": None, "correlation_id": ""}

    return app
