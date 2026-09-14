# Docker Compose Development Help

Run the complete local topology in containers: PostgreSQL, Valkey,
`opzhub-be-app`, `opzhub-web-app`, and `opzhub-ui-service`. This mode uses
named Docker volumes and a self-signed HTTPS certificate.

## Prerequisites

- Docker Desktop running with Docker Compose v2
- Ports `8102`, `8114`, and `8117` available locally
- Run commands from the repository root

Check Docker availability:

```powershell
docker version
docker compose version
```

## Start the default stack

```powershell
# Build first with visible output; useful when a build appears to stall.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --progress plain build

# Start the built services in the background.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d
```

This builds straight from the repo root, which contains every `apps/<id>/`
folder unfiltered — equivalent to the `managemyopz` product today, since it's
the only product with a populated `apps:` list. To build/run a stack scoped to
a specific product (`managemyopz` today; `managemyid`, `smartaicampus` once
they have real apps), use `--product` with the `dev-compose.sh` wrapper
instead of calling `docker compose` directly — see
[Start a product-scoped stack](#start-a-product-scoped-stack) below.

```bash
# Git Bash / MINGW64 — same effect as the two commands above, defaulted to managemyopz
bash infra/wrappers/dev-compose.sh --progress plain build
bash infra/wrappers/dev-compose.sh up -d

# Build a different product — pass --product only on the build. Once it has
# real apps/ folders listed for it:
bash infra/wrappers/dev-compose.sh --product managemyid --progress plain build
bash infra/wrappers/dev-compose.sh up -d   # starts whatever was just built — no --product needed here
```

`--product` only matters on the command that builds. Every other command
(`up`, `down`, `logs`, `ps`, ...) reuses whichever product the last build
packaged — it never needs, and never silently changes, the product on its
own.

Compose starts these default services:

| Service | Purpose | Access |
| --- | --- | --- |
| `postgres` | Persistent relational database | Internal only |
| `valkey` | Cache/session store | Internal only |
| `opzhub-be-app` | Java application backend | `127.0.0.1:8114` for debugging |
| `opzhub-web-app` | React SPA static server | `127.0.0.1:8117` for debugging |
| `opzhub-ui-service` | Public HTTPS gateway | `https://localhost:8102` |
| `init`, `certs` | One-time setup | Exit after completion |

Open `https://localhost:8102`. The development certificate is self-signed, so
accept the browser warning only for this local environment.

## First login

On the first PostgreSQL deployment, `opzhub-be-app` creates one
`admin@technosprint.net` account only when `id_user` contains no users. It
generates a random password, stores only its Argon2id hash in PostgreSQL, and
writes the one-time plaintext password to the mounted local credential file.

**PowerShell / CMD:**

```powershell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml exec opzhub-be-app cat /etc/opzhub/bootstrap-admin-password
```

**Git Bash / MINGW64** — disable automatic Windows path conversion for the
container path:

```bash
MSYS_NO_PATHCONV=1 docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml exec opzhub-be-app cat /etc/opzhub/bootstrap-admin-password
```

Log in with:

| Field | Value |
| --- | --- |
| Username | `admin@technosprint.net` |
| Password | output of the command above |
| Alt login | `technosprint/admin` |

The file exists only after an empty-database bootstrap. If users already exist,
bootstrap does not create a new account; an existing username `admin` is renamed
to `admin@technosprint.net` when that email is not already taken. The password
is not changed.

To set your own first password rather than use a generated one, create the
ignored `infra/.env` file before first start and set:

```dotenv
OPZHUB_BOOTSTRAP_ADMIN_PASSWORD=<strong-unique-password>
```

The configured password is never logged or written to the credential file.

## Platform admin login (no company)

A separate, company-less administrator manages companies and licenses
centrally (doc 35). It is created once at startup the same way the tenant
admin is, with its own generated password file.

Retrieve it:

```bash
# Git Bash / MINGW64
MSYS_NO_PATHCONV=1 docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml \
    exec opzhub-be-app cat /etc/opzhub/bootstrap-platform-admin-password
```

```powershell
# PowerShell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml `
    exec opzhub-be-app cat /etc/opzhub/bootstrap-platform-admin-password
```

Log in with:

| Field | Value |
| --- | --- |
| Username | `platformadmin` (bare username — no `/`, no `@`) |
| Password | output of the command above |

This account carries no `company_id` and its permission matrix is scoped
only to `company-setup` — it cannot see or act on any tenant module. To set
a fixed password instead of a generated one, add to `infra/.env` before
first container start:

```dotenv
OPZHUB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD=<strong-unique-password>
```

## Dev seed — restore company and admin data

Run the seed any time the database is missing the Technosprint company row,
the admin user is not linked to a company, or schema columns added in a later
design version were not applied by the automatic reconciler. The seed is
idempotent — safe to run on both fresh and existing databases.

The seed script lives **inside the `opzhub-be-app` image** at
`infra/wrappers/dev-seed.sh`. It is executed via `docker compose exec` so no
local `psql` client or bash script is needed — just Docker.

**Git Bash / MINGW64** (`MSYS_NO_PATHCONV=1` prevents path mangling):

```bash
MSYS_NO_PATHCONV=1 docker compose \
    -f infra/docker-compose.yml \
    -f infra/docker-compose.dev.yml \
    exec opzhub-be-app bash infra/wrappers/dev-seed.sh
```

**PowerShell** (no path conversion issue; backtick `` ` `` for line continuation):

```powershell
docker compose `
    -f infra/docker-compose.yml `
    -f infra/docker-compose.dev.yml `
    exec opzhub-be-app bash infra/wrappers/dev-seed.sh
```

What the seed does:

| Step | Database | Action |
| --- | --- | --- |
| 1 | `opzmain` | Ensures the `DEV` license row exists |
| 2 | `opzmain` | Ensures the `local` server row exists (points to the `postgres` container) |
| 3 | `opzmain` | Adds v2 columns to `company_information` if missing (schema migration guard) |
| 4 | `opzmain` | Upserts the Technosprint company row (slug, references, routing mode, status) |
| 5 | `opzuser` | Upserts the `admin` user and links it to the company |
| 6 | *(container fs)* | Reads `/etc/opzhub/bootstrap-admin-password` and prints the current password |

The seed **does not change the admin password**. It restores only the company
link, email, roles, and display name so the existing server-generated password
remains valid.

## Load code changes

`docker compose restart` restarts the current images only. Use the following
commands after changing Java, React, Python, Dockerfile, gateway, or Compose
files so Docker rebuilds images from the latest source and recreates services:

```powershell
# Rebuild every default service with visible output.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --progress plain build

# Recreate every default service from the rebuilt images.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --force-recreate
```

For a focused application update, rebuild and recreate only the changed
service:

```powershell
# Java backend source changes.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --progress plain build opzhub-be-app
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --force-recreate opzhub-be-app

# React web source changes.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --progress plain build opzhub-web-app
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --force-recreate opzhub-web-app

# Gateway configuration changes.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --progress plain build opzhub-ui-service
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d --force-recreate opzhub-ui-service

# Python AI/OCR/image source changes.
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --profile ai --progress plain build opzhub-be-core
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --profile ai up -d --force-recreate opzhub-be-core
```

Use `--profile ocr` or `--profile image` in the final two Python commands when
that is the selected capability. Confirm the refreshed containers afterward:

```powershell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml ps
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs --tail 100
```

## Verify health and status

```powershell
# Service state and health
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml ps

# Public gateway health
curl.exe -k https://localhost:8102/api/v1/opzhub/health

# Gateway-only health
curl.exe -k https://localhost:8102/healthz
```

A healthy backend returns an envelope with `"ok": true`.

## View logs

```powershell
# All services, follow live output
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f

# Last 100 lines from all services
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs --tail 100

# Individual services
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f opzhub-be-app
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f opzhub-web-app
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f opzhub-ui-service
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f postgres
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f valkey
```

Press `Ctrl+C` to stop following logs; containers continue running.

## Optional Python profile

Start the combined Python API and worker runtime when an AI, OCR, or
image-processing module is packed:

```powershell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml --profile ai up -d --build
```

The `opzhub-be-core` service runs both the Python API and OCR worker. View its
combined logs with:

```powershell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml logs -f opzhub-be-core
```

The same service also starts when only the `ocr` or `image` profile is selected.

## Start a product-scoped stack

The default stack above builds straight from the repo root, which contains
every `apps/<id>/` folder unfiltered. Because no `platform/config/product.yaml`
exists at the repo root, `opzhub-be-app` defaults its active product to
`managemyopz` at boot (doc 35 §4.5) — today that happens to include every app
in the repo, since `managemyopz` is the only product with a populated `apps:`
list in `platform/catalog/products.yaml`.

To run a stack that is provably scoped to one product's apps (useful once a
second product like `managemyid` gains real `apps/<id>/` folders, or to
sanity-check a release package before it ships), use the `dev-compose.sh`
wrapper — it takes a `--product` option, defaulting to `managemyopz`, and
does the packaging step for you before handing everything else straight to
`docker compose`.

### 1. Build with `--product`, start without it

```bash
# Git Bash / MINGW64, from the repo root.
bash infra/wrappers/dev-compose.sh --product managemyopz --progress plain build
bash infra/wrappers/dev-compose.sh up -d

# Switch to a different product later — pass --product again on the build
# only. Once managemyid/smartaicampus have real apps/ folders listed for them:
bash infra/wrappers/dev-compose.sh --product managemyid --progress plain build
bash infra/wrappers/dev-compose.sh up -d   # now starts managemyid — no --product repeated
```

`--product` is read only on a command that builds (`build`, or `up ...
--build`). Every other command (`up` without `--build`, `down`, `logs`,
`ps`, `exec`, ...) ignores it entirely and reuses whatever product the most
recent build packaged, so starting the stack can never silently drift to a
different product than what you built. If no product has ever been built yet
and you run a non-build command first, the wrapper errors out and tells you
to build with `--product` first; if you run a build with no `--product` at
all on a fresh checkout, it defaults to `managemyopz`.

Under the hood, a build with `--product KEY` runs
`infra/wrappers/package-product.sh --product KEY --out dist/current`
(copies the shared trees — `common/`, `modules/`, `platform/`, `infra/`,
`tools/`, `gateway/` — wholesale, plus only the `apps/<id>/` folders listed
for that product, and writes `dist/current/platform/config/product.yaml`),
then every subsequent command points `docker compose -f
dist/current/infra/docker-compose.yml -f
dist/current/infra/docker-compose.dev.yml` at that same output. `--product`
is still decided in exactly one place (`package-product.sh`, doc 35 §4.1) —
the wrapper only automates calling it before a build. Placeholder products
(`managemyid`, `smartaicampus`) refuse to package — `apps: []` — until at
least one `apps/<id>/` folder exists and is listed for them in
`platform/catalog/products.yaml`. `dist/current` is deleted and recreated on
every build — never hand-edit files under `dist/`, and don't expect two
products' stacks side by side (building a new product replaces the old
package; stop the running stack first if it's up).

If you'd rather run the steps yourself: `bash
infra/wrappers/package-product.sh --product managemyopz --out
dist/current`, then the same `docker compose -f
dist/current/infra/docker-compose.yml -f
dist/current/infra/docker-compose.dev.yml ...` commands directly.

### 2. Confirm the active product

```powershell
docker compose -f dist/current/infra/docker-compose.yml -f dist/current/infra/docker-compose.dev.yml logs opzhub-be-app | Select-String "Platform product seed"
```

Look for `Platform product seed: active product = managemyopz` (or the key
you packaged). This is written once at boot into the single-row
`platform_product` (`OPZMAIN`) and `company_active_product` (`OPZHUB`) tables.

## Stop or reset

```powershell
# Stop containers; keep database, cache, and local certificate volumes
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml down

# Stop containers and delete all local Docker volumes/data
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml down -v
```

Use `down -v` only when a full local reset is intended.

## Development security boundary

- Public traffic uses `https://localhost:8102`.
- The certificate is development-only and self-signed.
- Docker-internal connections are currently plaintext on the isolated
  `opzhub-internal` network.
- PostgreSQL and Valkey ports are never published to the host.
- The debug ports `8114` and `8117` bind only to `127.0.0.1`.
- Do not use development credentials or development environment files in a
  shared or production deployment.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| A service does not start | Run `docker compose ... ps` and its service-specific log command. |
| Browser certificate warning | Expected for the self-signed local certificate. |
| Port bind error | Stop the process occupying `8102`, `8114`, or `8117`. |
| Build appears to stall | Run `docker compose ... --progress plain build` to see the active layer. |
| Build uses stale layers | Run `docker compose ... --progress plain build --no-cache`, then `up -d`. |
| Docker Desktop not running (`open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`) | Start Docker Desktop and wait for it to report "Engine running", then retry. On Windows, confirm it's using the Linux engine (WSL2 or Hyper-V backend), not Windows containers. |
| Reset database/cache | Run `docker compose ... down -v`, then start again. |
| Login returns 401 after `up --force-recreate` | The company row or v2 schema columns may be missing. Run the dev seed (see [Dev seed](#dev-seed--restore-company-and-admin-data)). |
| Backend exits on startup with `duplicate key value violates unique constraint "application_catalog_product_code_key"` | Stale catalog row; fix already applied to the codebase. Rebuild: `docker compose ... build opzhub-be-app && docker compose ... up -d --force-recreate opzhub-be-app`. |
