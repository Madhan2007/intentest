# ManageMyOpz (opzhub)

Distributed hybrid ERP / plug-and-play application suite. This is the
**implementation repo root** — application code, infrastructure, and
customer packaging live here as siblings of `design/`.

- **`design/`** — architecture and design specs only (`design/docs/`).
  Design keeps evolving from user prompts; it does not contain application
  code. Read [design/docs/README.md](design/docs/README.md) first.
- **Everything else at this root** (`common/`, `modules/`, `solutions/`,
  `gateway/`, `infra/`, `platform/`, `tools/`) is the implementation,
  built against those specs.

**Model:** ManageMyOpz (**opzhub**). Release under `/home/tsuser/opzhub` (upgraded often). Site/state in `/etc/opzhub` and `/var/lib/opzhub` (one-time). Runtime user **tsuser**.

## Status

Kernel (Java + Python + React + Flutter + CLI) plus the shared `identity`
and `admin` modules are built and verified end to end (login screen ->
session -> blank dashboard). No sold application (HR, ticketing, ...) is
packed yet — see `platform/config/platform.yaml` `modules.enabled`.

## Usage

Four ways to run this, from a laptop inner-loop to AWS EC2 production —
see **[USAGE.md](USAGE.md)**:

1. Development — standalone (no Docker, memory db/cache, hot reload)
2. Development — Docker (full topology, named volumes, debug ports)
3. Production — Docker (real secrets/certs via `infra/.env`)
4. Production — AWS EC2 (host-bootstrapped, real bind mounts, `opzhubctl`)

Dedicated runbooks:

- [Standalone development](HELP-STANDALONE-DEVELOPMENT.md)
- [Docker Compose development](HELP-DOCKER-COMPOSE-DEVELOPMENT.md)
- [AWS EC2 production](HELP-AWS-EC2-PRODUCTION.md)


See [infra/volumes.md](infra/volumes.md) for what is intentionally
simplified in this pass.
