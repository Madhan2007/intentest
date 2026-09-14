# Usage Runbook

Four ways to run ManageMyOpz (opzhub). All four run the **same code** —
only the profile, storage engines, and TLS scope change (doc 17 §1).

| # | Mode | Command | DB / Cache | TLS |
| - | ---- | ------- | ---------- | --- |
| 1 | [Development — standalone](#1-development--standalone) | `mvn spring-boot:run` / `npm run dev` / `uvicorn` | memory | none |
| 2 | [Development — Docker](#2-development--docker) | `docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d` | Postgres + Valkey (containers) | public hop only, self-signed |
| 3 | [Production — Docker](#3-production--docker) | `docker compose -f infra/docker-compose.yml up -d` (+ `infra/.env`) | Postgres + Valkey (containers) | public hop only, real cert |
| 4 | [Production — AWS EC2](#4-production--aws-ec2) | same as #3, run on a bootstrapped EC2 host | Postgres + Valkey (containers) | public hop only, real cert |

**Every mode's internal hops** (opzhub-ui-service → opzhub-be-app/opzhub-web-app, opzhub-be-app →
Postgres/Valkey) **are plaintext** on the isolated Docker network for now.
Full internal mTLS is doc 12, not implemented yet — see "What's simplified"
at the bottom of this file.

---

## 1. Development — standalone

No Docker. Each process run directly, `db.type: memory` / `cache.type:
memory` (`platform/config/platform.yaml` dev defaults), plain HTTP.

Prerequisites: JDK 21, Maven, Node 20+, Python 3.12, Flutter SDK (for the
mobile shell only).

```bash
# Java kernel (identity + admin) — http://localhost:8114
cd common/backend && mvn spring-boot:run

# Web SPA — http://localhost:5173 (Vite dev server proxies /api/* to :8114/:8117)
cd common/frontend && npm install && npm run dev

# Python kernel (health only — no AI module packed yet) — http://localhost:8117
cd common/python
pip install fastapi "uvicorn[standard]" pyyaml pydantic
uvicorn services.ai.main:app --reload --port 8117

# Flutter shell (desktop/web/mobile)
cd common/mobile && flutter run --dart-define=OPZHUB_ORIGIN=http://localhost:8114

# CLI kernel
cd common/scripts && python opzhubctl doctor
```

Sign in at `http://localhost:5173`.

Use this mode for day-to-day feature work — it's the fastest inner loop
(hot reload on all three clients, no image builds).

---

## 2. Development — Docker

Full topology in containers (Postgres, Valkey, Java, web, Nginx), but
tuned for a laptop: **named Docker volumes** (no real host paths needed)
and **loopback-only extra ports** for IDE/debugger attach.

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml build --progress plain
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d

# https://localhost:8102            (public gateway, self-signed dev cert — browser will warn)
# http://127.0.0.1:8114/...          (direct backend access for a debugger)
# http://127.0.0.1:8117/...          (direct web container, bypassing Nginx)

docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f opzhub-be-app
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml down        # stop (keep volumes)
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml down -v     # stop + wipe DB/cache/certs
```

What the dev overlay changes vs. the base file (doc 17 §4): publishes
`127.0.0.1:8114` and `127.0.0.1:8117`. Everything else — service names,
images, volume names, cert generation — is identical to production, so
bugs found here reproduce in prod.

Optional Python core profile (only if an AI/OCR/mail module is later packed):
```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --profile ai build --progress plain
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --profile ai up -d
```

`opzhub-be-core` runs both the Python API and worker. Use `--profile ocr`
instead when only OCR is required.

---

## 3. Production — Docker

Same `infra/docker-compose.yml`, **no dev overlay**, driven by
`infra/.env` (copy from [`infra/.env.example`](infra/.env.example)) so
real secrets, real ports, and (optionally) real host paths replace the
laptop-friendly defaults.

```bash
cd infra
cp .env.example .env
# edit .env: strong POSTGRES_PASSWORD / VALKEY_PASSWORD.
# Leave OPZHUB_ETC_SRC/VARLIB_SRC/VARLOG_SRC unset to keep named volumes
# (simplest — good for a single prod VM that isn't EC2-bootstrapped), or
# point them at real host paths (see mode 4).

# Real public certificate: place BEFORE starting the stack, so the
# self-signed dev-cert step no-ops (it only generates one if absent):
#   /etc/opzhub/certs/public/fullchain.pem
#   /etc/opzhub/certs/public/privkey.pem
# (or the equivalent path inside the named volume — easiest is to switch to
# host paths per mode 4 so you can just `install` the files there directly)

docker compose -f docker-compose.yml build --progress plain
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml exec opzhub-be-app sh -c 'true'   # smoke: container is up
curl -f https://<your-domain>/healthz
```

Validate before going live:
```bash
cd ../common/scripts
python opzhubctl doctor --as-prod
```

`--as-prod` enforces: `security.allow_open_dev=false`, no `memory` stores,
`security.tls.mode=required`, and every `modules.enabled` entry exists on
disk (doc 08 §7). Note: `db.type`/`cache.type` for this Compose stack are
set via the compose file's own `DB_TYPE=postgres` / `CACHE_TYPE=valkey`
environment (not `platform.yaml`), so `doctor` here mainly checks the
YAML-level policy flags — review the compose environment block yourself
for the DB/cache values.

---

## 4. Production — AWS EC2

Mode 3, plus the one-time host bootstrap from doc 09 §5, and **real host
bind mounts** instead of named volumes (so `opzhubctl` running directly on
the EC2 host sees the same `/etc/opzhub`, `/var/lib/opzhub`,
`/var/log/opzhub` as the containers).

### One-time host setup (as root, once per instance)

```bash
# Ubuntu 24.04 LTS EC2 instance. Security group: 22 (restricted CIDR), 80, 443 only.
sudo bash infra/ec2-bootstrap.sh
# Installs Docker, creates tsuser (uid 2100), runs infra/wrappers/init-system.sh
# (creates /etc/opzhub, /var/lib/opzhub, /var/log/opzhub owned by tsuser).
```

### Ship the release

```bash
# From your build machine / CI: rsync or scp the repo (or a packaged
# tarball once `opzhubctl package` exists) to the instance.
rsync -az --exclude node_modules --exclude target --exclude .dart_tool \
  ./ ec2-user@<host>:/home/tsuser/opzhub/

ssh ec2-user@<host>
sudo chown -R tsuser:tsuser /home/tsuser/opzhub
sudo -u tsuser bash /home/tsuser/opzhub/infra/wrappers/init-home.sh
```

### Configure and start (as tsuser)

```bash
cd /home/tsuser/opzhub/infra
cp .env.example .env
vi .env
#   POSTGRES_PASSWORD=<strong secret>
#   VALKEY_PASSWORD=<strong secret>
#   OPZHUB_HOST_HTTP=80
#   OPZHUB_HOST_HTTPS=443
#   OPZHUB_ETC_SRC=/etc/opzhub
#   OPZHUB_VARLIB_SRC=/var/lib/opzhub
#   OPZHUB_VARLOG_SRC=/var/log/opzhub

# Real certificate (certbot/ACM-exported), placed before first start:
sudo install -o tsuser -g tsuser -m 0640 fullchain.pem /etc/opzhub/certs/public/fullchain.pem
sudo install -o tsuser -g tsuser -m 0640 privkey.pem   /etc/opzhub/certs/public/privkey.pem

docker compose -f docker-compose.yml build --progress plain
docker compose -f docker-compose.yml up -d
/usr/local/bin/opzhubctl migrate
/usr/local/bin/opzhubctl health --wait 120
```

### Day 2 operations

`opzhubctl` (installed once to `/usr/local/bin` by `init-system.sh`) drives
the same Compose stack from the host (doc 06 §3):

```bash
opzhubctl status                 # running/stopped per service, exit 3 if down
opzhubctl logs opzbe -f          # tail opzhub-be-app
opzhubctl restart opzgw          # restart just Nginx
opzhubctl release                # version + site paths + module list
opzhubctl doctor --as-prod       # fail-closed prod validation
```

Backups and health cron: `infra/wrappers/system/opzhub-run-cron` (installed
by `init-system.sh`), scheduled per [design/docs/architecture/16-jobs-crontab.md](design/docs/architecture/16-jobs-crontab.md).

### Upgrade

```bash
# Ship a new release tree to /home/tsuser/opzhub (replaces app code only —
# /etc/opzhub, /var/lib/opzhub, /var/log/opzhub are untouched, doc 17 §7).
docker compose -f docker-compose.yml up -d --build
opzhubctl migrate      # create/alter only, never wipes rows (doc 19)
opzhubctl health --wait 120
```

---

## What's simplified in every mode (documented follow-ups)

- **Internal TLS/mTLS** (doc 12): only the public Nginx hop is HTTPS.
  opzhub-ui-service→opzhub-be-app/opzhub-web-app and opzhub-be-app→Postgres/Valkey are plaintext
  on the isolated `opzhub-internal` Docker network.
- **Broker**: `broker.type=memory` only — Valkey Streams (doc 11) isn't
  implemented in Java/Python yet, so no compose profile sets `valkey` there.
- **`opzhubctl package`** (doc 01 §6): no customer-subset packaging tool
  yet — the EC2 steps above ship the full working tree via `rsync`.
- **OAuth2 / face / fingerprint login** (doc 18): only local password.
- **Full RBAC/ABAC** (doc 18 §3): placeholder role → matrix mapping.

See the top-level [README.md](README.md) for the full "not implemented
yet" list, and [infra/volumes.md](infra/volumes.md) for the volume model.
