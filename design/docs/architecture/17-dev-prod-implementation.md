# 17 — Production Implementation Model (dev and prod)

This platform is **designed to be implemented and shipped**. Production on Ubuntu 24.04 + Docker Compose + `tsuser` is the target system, not a later rewrite. Development is a **first-class profile of the same code**, not a fork of `common/`.

Wrappers under `infra/wrappers/` (`opzhubctl`, job/cron, init-system) are already the production operator surface. Application runtimes (Java, Python, React, Flutter, Compose files) are implemented against this document set.

## 1. Two modes, one product

| Mode | `solution.profile` | Who | What it is |
| ---- | ------------------ | --- | ---------- |
| **Development** | `dev` | Engineers on a laptop or a shared dev box | Same modules and APIs; hot reload; optional TLS; test stores allowed |
| **Staging** | `staging` | Pre-prod | Production topology (Compose, TLS, Postgres, Valkey); may use smaller sizes |
| **Production** | `prod` | Customer appliance | [09](09-docker-nginx-ec2.md) + [15](15-non-root-execution.md) + [12](12-transport-security-tls.md) |

Rules:

1. **One source tree.** Dev and prod do not maintain parallel copies of kernel or modules.
2. **YAML and overlays switch the mode**, not `#ifdef` product forks.
3. **`opzhubctl doctor` enforces the profile.** `prod` rejects open auth, plaintext, `db.type: memory`, and running as root.
4. **Artifacts promote.** `opzhubctl package` builds **that customer’s subset** (not the 50+ catalog). Staging/prod extract that tarball to `/home/tsuser/opzhub`. Dev may run from a git worktree with many folders; delivery must not.

## 2. What production implementation must deliver

When code work happens, it must match this shape — do not invent a different layout “just for the first sprint.”

| Piece | Production shape |
| ----- | ---------------- |
| Host | Ubuntu 24.04, user `tsuser` (2100), Docker Compose |
| Release | `/home/tsuser/opzhub` replaced on upgrade |
| Site / secrets | `/etc/opzhub` (once) |
| State / logs | `/var/lib/opzhub`, `/var/log/opzhub` (once) |
| Operator | `/usr/local/bin/opzhubctl` → `start` / `stop` / `status` / `release` (`opzgui`, `opzbe`, `opzpy`, `opzgw`) |
| Public door | Nginx 80/443 only |
| Clients | Browser SPA **and** Flutter apps; both call the same public HTTPS/WSS APIs |
| Data | `DataClient` / `CacheClient` / `BrokerClient` selected by YAML |

Implementation order (do not skip the kernel):

1. `platform.yaml` loader + clients (Java + Python) with `postgres`/`valkey` **and** `memory` (tests / `profile=dev` only).
2. Compose files: **prod** compose + **dev** overlay (section 4).
3. Nginx + health + `opzhubctl doctor`.
4. Web shell (`common/frontend`) + Flutter shell (`common/mobile`) + field registry from `common/contracts/fields`.
5. `opzhubctl module-gen` (web map, Java map, Python map, **Flutter map**).
6. Shared modules `identity` + `admin`, then **one** sold app at a time (`hr` or `ticketing`).
7. Prove three packs: HR-only, ticketing-only, HR+ticketing (`opzhubctl package` extra-folder guard).
8. Further catalog apps as new folders; never add them to unrelated customer packs.
9. Staging host extract of **that** pack; `profile=prod` smoke.

## 3. Development profile (required)

Engineers may run **many** catalog folders locally. Customer delivery is still a **subset pack**. Do not treat a fat local worktree as the production tarball.

Allowed only when `solution.profile: dev` (or `OPZHUB_PROFILE=dev`):

| Relaxation | Dev | Prod |
| ---------- | --- | ---- |
| `security.allow_open_dev` | optional, default false | forbidden |
| `security.tls.allow_insecure_dev` | optional (mkcert or HTTP on loopback) | forbidden |
| `db.type: memory` / `cache.type: memory` / `broker.type: memory` | unit/integration tests; optional local demo | forbidden |
| Hot reload | Vite + `flutter run` + Spring/uvicorn `--reload` | off |
| Published ports | loopback 8114/8117/8109 for IDE attach | internal Docker network only |
| Identity | real `identity` module **or** a documented mock user | real identity |

Forbidden in every profile: putting secrets in the git tree; running app JVMs as root; importing a sibling module’s internals.

Local commands (illustrative):

```bash
# API stack (same services as prod, overlay for bind-mounts / published ports)
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d

# Web
cd common/frontend && npm run dev     # Vite → proxy to local gateway

# Mobile
cd common/mobile && flutter run --flavor dev --dart-define=OPZHUB_ORIGIN=https://127.0.0.1:8102

# Operator (host or compose)
opzhubctl status
opzhubctl doctor                      # warns, does not apply prod-hard fails unless --as-prod
```

`opzhubctl doctor` in `dev`: warn on missing TLS. `opzhubctl doctor --as-prod` (CI on the release tarball): fail closed with production rules.

## 4. Compose: prod file + dev overlay

Two files, not two products:

```
infra/docker-compose.yml              # production contract (doc 09)
infra/docker-compose.dev.yml          # overlay: ports, volumes, NODE_ENV, SPRING_DEVTOOLS
```

| File | Used when |
| ---- | --------- |
| `docker-compose.yml` only | staging, prod, customer appliance |
| both files | laptop / shared dev |

Dev overlay may:

- Bind-mount `./` read-write for hot reload (prod mounts release **read-only**).
- Publish `127.0.0.1:8114` etc. for IDE debuggers.
- Use a compose project name `opzhub-dev` so it does not clash with a local prod-like stack.
- Mount a **dev** `platform.override.yaml` (`profile: dev`).

Dev overlay must **not**:

- Change service names (`opzhub-be-app`, `opzhub-web-app`, `opzhub-be-core`, …) — `opzhubctl` and Nginx upstreams stay stable.
- Disable the plugin-play scan.
- Bake `allow_open_dev: true` into the prod file.

`OPZHUB_RUNTIME=compose|host|auto` still applies ([06](06-scripts-design.md)). Dev often uses Compose; a single-process `host` start of `opzbe` is valid for Java debugging.

## 5. Test pyramid (both modes)

| Layer | Stores | Clients |
| ----- | ------ | ------- |
| Unit | `memory` Data/Cache/Broker | no Docker |
| Module contract | Testcontainers Postgres/Valkey **or** Compose `dev` | HTTP against kernel |
| Plugin-play | Kernel-only boot; add/remove a module folder; regenerate maps | web + Flutter smoke |
| Soak / prod-like | `profile: staging` or `prod` YAML, real TLS | Compose without the dev overlay |

CI must run `opzhubctl doctor --as-prod` on the packaged customer tree before a release is tagged.

## 6. Client builds (web + mobile)

| Client | Dev | Production |
| ------ | --- | ---------- |
| Web | Vite dev server behind or beside Nginx | Static build in `opzhub-web-app` image, served as `opzgui` |
| Flutter | `flutter run --flavor dev` | `flutter build apk/ipa --flavor prod` with public origin + cert pin |

Both clients consume **the same** `/api/v1/opzhub`, `/api/v1/ai`, `/ws/opzhub`. There is no mobile-only business API. Details: [03](03-frontend-design.md) §14.

Store listings (Play / App Store) are **solution-branded** apps built from `common/mobile` + selected `modules/*/mobile`. They are not a second product.

## 7. Promotion path

```
git worktree (dev)
    → CI: test + module-gen + images + doctor --as-prod
    → release tarball + images (version in platform/RELEASE)
    → staging host: extract to /home/tsuser/opzhub, init-home, opzhubctl start, migrate
    → prod host: same steps, profile=prod, public certs in /etc/opzhub
```

Upgrade never copies `/etc/opzhub` or `/var/lib/opzhub`. Flutter store builds are versioned with the same `platform/RELEASE` `VERSION`.

## 8. What implementers must not do

- A “dev-only” kernel that cannot run in Compose prod.
- A mobile app that calls Postgres or Valkey, or a second REST shape.
- Hardcoding `localhost` in module code (origin comes from public config / `--dart-define`).
- Shipping `profile: dev` YAML on a customer appliance (`doctor` fails).
