# 09 — Docker, Nginx & EC2 Infrastructure

Design-only for Compose/Nginx files. **Non-root wrappers are implemented** under `infra/wrappers/`. Implementations must follow this specification.

## 1. Hosting model

- Single AWS EC2 instance, **Ubuntu 24.04 LTS**.
- **Docker Compose** orchestrates all services.
- **One public entry:** Nginx `:443` (HTTPS/WSS) only. There is no `:80` listener and no HTTP-to-HTTPS redirect.
- Internal **bridge network** `opzhub-internal`. Application ports are not published to `0.0.0.0` except Nginx (and SSH on the instance security group).
- **Non-root:** Compose `user: "2100:2100"` (`tsuser`) for ERP, AI, worker, web, and Nginx; entrypoints `infra/wrappers/run-*.sh`. Host operator is **`tsuser`**. Spec: [15](15-non-root-execution.md).
- **TLS on every hop:** apps listen HTTPS on **non-default internal ports** (ERP `8114`, AI `8117`, web `8109`) — not `8443`/`8080`/`8000`. Postgres and Valkey use TLS. Spec: [12](12-transport-security-tls.md). Broker is Valkey Streams. Kafka is optional **later** and is **not** in this Compose drop: [11](11-broker-selection.md).

## 2. Services and network

```
opzhub-internal (bridge) — all internal listeners TLS
  opzhub-ui-service  host 443→8102 (process **tsuser** uid 2100; HTTPS only, no HTTP listener)
  opzhub-be-app      8114 internal HTTPS
  opzhub-be-core     8117 internal HTTPS
  postgres        5432 TLS internal
  valkey          6380 TLS internal (cache + Streams broker)
  opzhub-web-app  8109 internal HTTPS (SPA) — Nginx proxies `/`
```

**Internal HTTPS port map (do not use 8080 / 8000 / 8443):**

| Service | Port | Role |
| ------- | ---- | ---- |
| `opzhub-be-app` | **8114** | Spring Boot HTTPS + WSS |
| `opzhub-be-core` | **8117** | FastAPI HTTPS |
| `opzhub-web-app` | **8109** | SPA HTTPS |

These ports are Compose-internal only. The browser always uses 443.

DNS names = Compose service names. Java/Python YAML hosts: `postgres`, `valkey`, `opzhub-be-app`, `opzhub-be-core`.

### 2.1 Compose profiles (plugin-play)

| Profile | Extra services | When |
| ------- | -------------- | ---- |
| default | opzhub-ui-service, opzhub-web-app, opzhub-be-app, postgres, valkey | Always (Valkey covers cache, WS Pub/Sub, and default broker) |
| `ai` | opzhub-be-core | Any AI module **or** face / fingerprint-template login |
| `ocr` | opzhub-be-core, ocr-models volume | `modules/ocr` present |
| `image` | opzhub-be-core | `modules/image-processing` |

Kafka is **not** a Compose profile in this drop. A later drop may add `kafka` when `KafkaBrokerServer` exists ([11](11-broker-selection.md)).

If OCR folder is removed, omit the `ocr` profile and OCR model volume. Face login still uses `ai` + `identity-models`. `opzhubctl compose` selects profiles from the manifest and auth methods.

## 3. `docker-compose.yml` specification

Implementation must include:

### 3.1 Top-level

- `name: opzhub` (or solution id)
- `networks.opzhub-internal.driver: bridge`
- `volumes:`
  - `pg-data` — PostgreSQL data
  - `valkey-data` — Valkey persistence (`appendonly yes` or equivalent)
  - `ocr-models` — profile `ocr`
  - `identity-models` — profile `ai` when face or fingerprint-template is on
  - `ai-staging` — inbound scans

### 3.2 Environment

- `env_file: solutions/${SOLUTION_ID}/config/secrets.env`
- `PLATFORM_CONFIG_PATH=/platform/config/platform.yaml`
- Mount `platform/config` and `solutions/<id>/config` read-only

### 3.3 Dependencies (`depends_on` + healthchecks)

| Service | Wait for healthy |
| ------- | ---------------- |
| opzhub-be-app | postgres, valkey |
| opzhub-be-core | valkey |
| opzhub-ui-service | opzhub-be-app, opzhub-web-app, opzhub-be-core (ai profile: optional with resolver) |

Healthchecks:

- postgres: `pg_isready -U opzhub_app -d opzhub`
- valkey: `valkey-cli --tls -a "$VALKEY_PASSWORD" ping` (or equivalent)
- opzhub-be-app: `GET https://127.0.0.1:8114/api/v1/opzhub/health` with internal CA
- opzhub-be-core: `GET https://127.0.0.1:8117/api/v1/ai/health` with internal CA

### 3.4 Resource notes (EC2)

OCR workers: `mem_limit` and `cpus` set in solution overlay so a deskew job cannot starve PostgreSQL. Postgres and Valkey get reserved memory in the same overlay.

### 3.5 Persistence

```
postgres:
  volumes: [pg-data:/var/lib/postgresql/data]
valkey:
  command: >
    valkey-server
    --tls-port 6380 --port 0
    --tls-cert-file /certs/internal/valkey.crt
    --tls-key-file /certs/internal/valkey.key
    --tls-ca-cert-file /certs/internal/ca.crt
    --requirepass ${VALKEY_PASSWORD}
    --appendonly yes
  volumes: [valkey-data:/data, certs-internal:/certs/internal:ro]
```

Hourly DB dumps (`pg_dump -Fc`) go to `/var/lib/opzhub/backup/db` and optionally FTPS/SFTP. Cron: `opzhub-run-cron db-backup`. Recovery: `opzhubctl restore`. Spec: [19](19-db-backup-migrate.md).

### 3.6 Example service skeleton (illustrative YAML in this doc only)

```yaml
# ILLUSTRATIVE — do not treat as the repo compose file
services:
  opzhub-ui-service:
    image: nginx:1.27-alpine
    ports: ["443:8102"]
    user: "2100:2100"
    read_only: true
    cap_drop: ["ALL"]
    security_opt: ["no-new-privileges:true"]
    entrypoint: ["bash", "/home/tsuser/opzhub/infra/wrappers/run-nginx.sh"]
    tmpfs: ["/home/tsuser/opzhub/tmp:size=64m,mode=1777,uid=2100,gid=2100"]
    volumes:
      - ./:/home/tsuser/opzhub:ro
      - opzhub-data:/home/tsuser/opzhub/data
    networks: [opzhub-internal]
    depends_on:
      opzhub-be-app: { condition: service_healthy }
      opzhub-web-app: { condition: service_started }

  opzhub-be-app:
    build: { context: ., dockerfile: common/backend/Dockerfile }
    user: "2100:2100"
    read_only: true
    cap_drop: ["ALL"]
    security_opt: ["no-new-privileges:true"]
    entrypoint: ["bash", "/home/tsuser/opzhub/infra/wrappers/run-opzhub-engine.sh"]
    environment:
      PLATFORM_CONFIG_PATH: /etc/opzhub/platform.override.yaml
    networks: [opzhub-internal]
    expose: ["8114"]
    volumes:
      - ./:/home/tsuser/opzhub:ro
      - opzhub-data:/home/tsuser/opzhub/data
    tmpfs: ["/home/tsuser/opzhub/tmp:size=256m,mode=1777,uid=2100,gid=2100"]

  opzhub-be-core:
    profiles: ["ai", "ocr"]
    build: { context: ., dockerfile: common/python/Dockerfile.api }
    user: "2100:2100"
    read_only: true
    cap_drop: ["ALL"]
    security_opt: ["no-new-privileges:true"]
    entrypoint: ["bash", "/home/tsuser/opzhub/infra/wrappers/run-opzhub-be-core.sh"]
    expose: ["8117"]
    networks: [opzhub-internal]
    volumes:
      - ./:/home/tsuser/opzhub:ro
      - opzhub-data:/home/tsuser/opzhub/data
    tmpfs: ["/home/tsuser/opzhub/tmp:size=256m,mode=1777,uid=2100,gid=2100"]

  postgres:
    image: postgres:16
    volumes: [pg-data:/var/lib/postgresql/data, certs-internal:/certs/internal:ro]
    networks: [opzhub-internal]
    expose: ["5432"]

  valkey:
    image: valkey/valkey:8
    volumes: [valkey-data:/data, certs-internal:/certs/internal:ro]
    networks: [opzhub-internal]
    expose: ["6380"]

  opzhub-web-app:
    build: { context: ., dockerfile: common/frontend/Dockerfile }
    user: "2100:2100"
    read_only: true
    cap_drop: ["ALL"]
    security_opt: ["no-new-privileges:true"]
    entrypoint: ["bash", "/home/tsuser/opzhub/infra/wrappers/run-web.sh"]
    expose: ["8109"]
    networks: [opzhub-internal]
    volumes:
      - ./:/home/tsuser/opzhub:ro
      - opzhub-data:/home/tsuser/opzhub/data
    tmpfs: ["/home/tsuser/opzhub/tmp:size=64m,mode=1777,uid=2100,gid=2100"]

networks:
  opzhub-internal:
    driver: bridge

volumes:
  pg-data:
  valkey-data:
  ocr-models:
  identity-models:
  ai-staging:
  opzhub-data:
```

Build context must be the **composed customer tree** so deleted modules are not in the image.

## 4. Nginx specification

Listen **8102 SSL** inside the container (host **443**). No HTTP listener and no redirect — nginx no longer publishes a plaintext port. Nginx process user **`tsuser` (2100)** — no bind to 80/443 inside the container. Path-based routing. **WSS** upgrade for `/ws/opzhub`. Upstream to apps is **HTTPS** with internal CA verification. Full TLS contract: [12](12-transport-security-tls.md). Non-root wrappers: [15](15-non-root-execution.md).

### 4.1 Location map

| Location | Upstream | Notes |
| -------- | -------- | ----- |
| `/api/v1/opzhub/` | `https://opzhub-be-app:8114` | mTLS / `proxy_ssl_verify on` |
| `/api/v1/ai/identity/` | — | **404**. Bio verify is Java → opzhub-be-core only |
| `/api/v1/ai/mail/` | — | **404**. SMTP/IMAP is Java → opzhub-be-core only ([23](23-mail-send-receive.md)) |
| `/api/v1/ai/` | `https://opzhub-be-core:8117` | omit location if AI profile off |
| `/ws/opzhub` | `https://opzhub-be-app:8114` | Upgrade + SSL verify; long timeouts |
| `/ws/ai` | `https://opzhub-be-core:8117` | Optional |
| `/healthz` | nginx stub or erp health | LB / EC2 (HTTPS on 443) |
| `/` | `https://opzhub-web-app:8109` | SPA |

### 4.2 Required proxy headers

All API locations:

- `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`
- `X-Correlation-ID` generated if missing

WebSocket location **additionally**:

```
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;
proxy_buffering off;
```

### 4.3 SSL (public and upstream)

- Public: container `listen 8102 ssl`; host maps 443→8102. No HTTP listener or redirect. HSTS in prod.
- Upstream: `proxy_pass https://...` with `proxy_ssl_trusted_certificate` (internal CA). Do not proxy to plaintext `8080`/`8000`/`8443` in prod.
- Public certs: `/certs/public`. Internal CA: `/certs/internal`.
- Browser uses **WSS** (`wss://host/ws/opzhub`).

### 4.4 Illustrative `nginx.conf` blocks (spec text)

```nginx
# ILLUSTRATIVE — prod: TLS public + TLS upstream (see doc 12 for full blocks)
upstream opzhub_engine { server opzhub-be-app:8114; }
upstream ai_engine  { server opzhub-be-core:8117; }
upstream web_spa    { server opzhub-web-app:8109; }

server {
  listen 8102 ssl;
  http2 on;
  ssl_certificate     /etc/opzhub/certs/public/fullchain.pem;
  ssl_certificate_key /etc/opzhub/certs/public/privkey.pem;
  ssl_protocols       TLSv1.2 TLSv1.3;
  client_max_body_size 32m;

  location /api/v1/opzhub/ {
    proxy_pass https://opzhub_engine;
    proxy_ssl_trusted_certificate /etc/opzhub/certs/internal/ca.crt;
    proxy_ssl_verify on;
    proxy_ssl_name opzhub-be-app;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto https;
  }

  # AI profile: OCR jobs public; bio + mail protocol are Java → opzhub-be-core only
  location /api/v1/ai/identity/ { return 404; }
  location /api/v1/ai/mail/ { return 404; }

  location /ws/opzhub {
    proxy_pass https://opzhub_engine;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_ssl_trusted_certificate /etc/opzhub/certs/internal/ca.crt;
    proxy_ssl_verify on;
    proxy_ssl_name opzhub-be-app;
    proxy_read_timeout 3600s;
    proxy_buffering off;
  }

  location /healthz {
    access_log off;
    return 200 "ok\n";
    add_header Content-Type text/plain;
  }

  location / {
    proxy_pass https://web_spa;
    proxy_ssl_trusted_certificate /etc/opzhub/certs/internal/ca.crt;
    proxy_ssl_verify on;
    proxy_ssl_name web;
  }
}
```

Generated Nginx (optional later): omit `/api/v1/ai/` when AI modules are absent so probes fail fast instead of 502.

## 5. Ubuntu 24.04 EC2 bootstrap (exact steps for later script)

The implementation script (`infra/ec2-bootstrap.sh`) must perform the following **silently in the background** after install (`docker compose up -d` **as `tsuser`, never as root**). Design of the steps:

1. `apt-get update` and unattended upgrades policy as required by ops.
2. Install Docker Engine from Docker’s Ubuntu repo (not obsolete `docker.io` only if policy says official engine).
3. Install Docker Compose plugin (`docker compose` v2).
4. Create user **`tsuser`** (uid **2100**) and add it to group `docker` (document logout/login). Do not run the stack as `root`.
5. Enable Docker service (`systemctl enable --now docker`).
6. As **root**, once: `infra/wrappers/init-system.sh` (`/etc/opzhub`, `/var/lib/opzhub`, `/var/log/opzhub`, `/usr/local/bin/opzhub-*`). Secrets stay in `/etc/opzhub/secrets.env` (**0640**, `root:tsuser`).
7. Copy/extract the **release** to `/home/tsuser/opzhub`. As tsuser: `init-home.sh`. Do not extract over `/etc` or `/var`.
8. As `tsuser`: `docker compose` from `/home/tsuser/opzhub`.
9. As `tsuser`: `/usr/local/bin/opzhubctl migrate` and `health --wait`. Cron uses `/usr/local/bin/opzhub-run-cron` ([16](16-jobs-crontab.md)).

Illustrative command sequence (for the future script, not executed here):

```bash
# ILLUSTRATIVE — future infra/ec2-bootstrap.sh
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
id tsuser >/dev/null 2>&1 || useradd --system --uid 2100 --create-home --home-dir /home/tsuser --shell /usr/sbin/nologin tsuser
usermod -aG docker tsuser
# copy release to /home/tsuser/opzhub, then:
bash /home/tsuser/opzhub/infra/wrappers/init-system.sh
runuser -u tsuser -- bash /home/tsuser/opzhub/infra/wrappers/init-home.sh
runuser -u tsuser -- bash -c 'cd /home/tsuser/opzhub && docker compose --profile ai --profile ocr up -d'
```

Security group: 22 (restricted CIDR), 443 only (no 80 — nginx has no HTTP listener). No 5432, 6379/6380, **8114**, **8117**, **8109**, 8124, 9093 from the internet.

## 6. Observability on the box

- `docker compose logs -f` via `opzhubctl`
- Rotate container logs (daemon.json `max-size`)
- `/healthz` for ALB/NLB later

## 7. Plugin-play impact on infra

| Removed module | Compose / Nginx effect |
| -------------- | ---------------------- |
| ocr | drop `ocr` profile, workers, models volume |
| image-processing | drop image queues; may keep opzhub-be-core if documents remain |
| all AI | drop opzhub-be-core, `/api/v1/ai/` location |
| notifications | WS still exists in kernel but no business topics; can leave Valkey |
| mail | drop SMTP/IMAP workers if no other Python need; keep `/api/v1/ai/mail/` 404 |
| ledger | no ERP finance routes; postgres still required for identity |

Postgres and Valkey remain kernel infrastructure even if all business modules are removed.

## 8. Development overlay

Production compose is this document. Laptops add `infra/docker-compose.dev.yml` (ports on loopback, bind-mounts, hot reload). Do not put those relaxations in the prod file. Flutter and Vite talk to the **same** Nginx public ports. Full rules: [17 — Production implementation](17-dev-prod-implementation.md).

## 9. Mobile clients

Flutter apps are **not** Compose services. They call host `:443` (or the dev published HTTPS port) with the same paths as the browser. No extra security-group ports for mobile.
