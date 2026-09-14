# ManageMyOpz — Design Specification

This folder is **design only** — architecture and specification documents
under [`docs/`](docs/README.md). It does not contain application code,
Compose files, or Nginx config; those live at the repository root (see the
top-level [README.md](../README.md)).

Design keeps evolving: documents here are updated as requirements are
clarified, independent of the implementation's release cadence.

Start here: [docs/README.md](docs/README.md).

## Run it locally (dev profile — memory db/cache, no Docker required)

```bash
# Java kernel (identity + admin), http://localhost:8114
cd common/backend && mvn spring-boot:run

# Web SPA, http://localhost:5173 (proxies /api/* to :8114)
cd common/frontend && npm install && npm run dev

# Python kernel (health only — no AI module packed yet), http://localhost:8117
cd common/python && pip install fastapi "uvicorn[standard]" pyyaml pydantic
uvicorn services.ai.main:app --reload --port 8117

# Flutter shell (desktop/web/mobile)
cd common/mobile && flutter run --dart-define=OPZHUB_ORIGIN=http://localhost:8114

# CLI kernel
cd common/scripts && python opzhubctl doctor
```


## Run the full stack in Docker (Postgres + Valkey + TLS gateway)

```bash
docker compose -f infra/docker-compose.yml up -d --build
# https://localhost:8102  (self-signed dev cert — browser will warn, that's expected)
docker compose -f infra/docker-compose.yml down   # stop; add -v to also wipe volumes
```

See [infra/volumes.md](infra/volumes.md) and doc 09/12 for what is
intentionally simplified in this pass (internal mTLS is not wired yet).
