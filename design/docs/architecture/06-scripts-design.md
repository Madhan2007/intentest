# 06 — Scripts Design

## 1. Role

Operational and feature scripts share one CLI kernel (`opzhubctl`) so every customer appliance has the same verbs. Feature-specific commands live in **module script folders** and disappear when that folder is removed.

Scripts are first-class citizens of the plugin-play model, equal to frontend and backend.

## 2. Principles

| Rule | Meaning |
| ---- | ------- |
| Same clients | Scripts use `DataClient` / `CacheClient` from the Python kernel (or a thin Java tool if a command must run inside the ERP image). Default implementation language: **Python**, same YAML flags. |
| No silent SQL | Break-glass `psql` only under `opzhubctl doctor --raw`. |
| Idempotent migrations | `opzhubctl migrate` can run twice. |
| Discover commands | Kernel does not hardcode `ocr seed`. |
| Safe by default | Destructive commands require `--yes` and a solution profile that is not `prod` unless `--force-prod`. |
| Non-root | `opzhubctl` and app wrappers run as **`tsuser`** (uid 2100) and refuse UID 0. See `infra/wrappers/` and [15](15-non-root-execution.md). |

## 3. Process control CLI (implemented)

Operators start and stop **runtimes**, not module folders. Service names are short `opz*` ids:

| Name | Process | Wrapper |
| ---- | ------- | ------- |
| `opzgui` | frontend SPA | `run-web.sh` |
| `opzbe` | Java backend | `run-opzhub-engine.sh` |
| `opzpy` | Python API and workers | `run-opzhub-be-core.sh` |
| `opzgw` | public TLS (nginx) | `run-nginx.sh` |

```
opzhubctl start              # all present services
opzhubctl start opzgui opzbe opzpy
opzhubctl stop opzbe
opzhubctl restart all
opzhubctl status
opzhubctl logs opzgui -f
opzhubctl release            # version, site paths, modules, process state
```

**Stable vs release:** `/usr/local/bin/opzhubctl` is installed **once** by `init-system.sh` (root). It `exec`s the **current** `/home/tsuser/opzhub/infra/wrappers/run-opzhubctl.sh`, so verbs upgrade with the app. PIDs: `/var/lib/opzhub/run/svc/<id>.pid`. Logs: `/var/log/opzhub/svc/<id>.log`. Neither path is in the upgrade tarball.

**Runtime mode:** `OPZHUB_RUNTIME=auto|compose|host`. Auto uses Docker Compose when a compose file exists (`/etc/opzhub/docker-compose.yml` or `infra/docker-compose.yml`); otherwise host pid-file supervision. Compose `start all` is a single `up -d` so `depends_on` order is kept.

Python `opzpy` joins `all` when `modules/ocr`, `modules/image-processing`, or `modules/mail` exists, when `security.auth.methods` includes `face` or `fingerprint` (template), or `OPZHUB_ENABLE_AI=1` / `OPZHUB_ENABLE_BIO=1`. It starts both API and worker processes in `opzhub-be-core`.

`release` reads `platform/RELEASE` from the **current** extract plus site fields from `/etc/opzhub` (`OPZHUB_SOLUTION_ID`).

Kernel data verbs (`migrate`, `doctor`, `module-gen`, …) pass through to Python when `common/scripts` is installed; they are not required for start/stop/status.

## 4. Kernel directory

```
common/scripts/
  opzhubctl                         # entry (invoked via infra/wrappers/run-opzhubctl.sh)
  opzhub_scripts/
    cli.py
    config.py                    # load platform.yaml + solution override
    logging.py
    commands/
      health.py                  # ping nginx/erp/ai/db/cache/broker
      migrate.py                 # kernel + each present module db/
      rollback.py                # explicit, per module version
      doctor.py
      module_gen.py              # regenerate frontend/java/python maps
      compose.py                 # wrapper: up -d, down, logs
      users.py                   # only if identity module present — actually identity owns this
```

Kernel commands that **must always exist** (Python, later): `health`, `migrate`, `doctor`, `module-gen`, `compose`. Process verbs above are bash and already exist.

Commands that belong to modules: seed data, OCR model download, chart of accounts import, inventory cycle-count loaders.

## 5. Module scripts contract

```
modules/<feature>/scripts/
  cli.yaml
  commands/
    seed.py
    ...
```

`cli.yaml` example:

```yaml
id: ledger
commands:
  - name: seed-coa
    entry: commands/seed_coa.py:run
    help: Load chart of accounts from CSV
    args:
      - { name: file, required: true }
  - name: close-period
    entry: commands/close_period.py:run
    help: Operational period close (calls ERP HTTP, not raw SQL)
```

`opzhubctl ledger seed-coa --file ./coa.csv` exists only if `modules/ledger/scripts` exists.

## 6. Command execution context

Every `run(ctx, args)` receives:

```
ctx.solution_id
ctx.config                 # merged YAML
ctx.data                   # DataClient
ctx.cache                  # CacheClient
ctx.http_core              # https://opzhub-be-app:8114 (mTLS)
ctx.http_ai                # https://opzhub-be-core:8117
ctx.module_id
```

Period close and posting **must** call ERP HTTP so ACID rules stay in Java. Scripts do not `UPDATE ledger_entry`.

Seeds for master data may use `DataClient` inside the module’s own tables if the module documents it; prefer HTTP for anything that has validation beyond NOT NULL.

## 7. Migration runner

Full contract: [19 — DB backup & migrate](19-db-backup-migrate.md).

`opzhubctl migrate` (also on `opzhubctl start` when `backup.migrate_on_start: true`):

1. Apply kernel migrations (`common/backend/db`, table `kernel_schema_migrations`).
2. Sort **present** modules by `requires`.
3. Apply `modules/<id>/db/*.sql` not yet recorded (checksum of each file).
4. **Create / alter** only. Do not `DROP DATABASE` or `TRUNCATE` business tables. `DROP COLUMN`/`DROP TABLE` only in a later numbered file that is expand/contract safe.
5. Record `(module_id, version, checksum, applied_at)`.

First install: empty database → all CREATE scripts for kernel + packed applications (HR-only never creates `tkt_*`).

Upgrade: already-applied versions are skipped; existing rows are left in place. Checksum change on an applied file **fails** the migrate (no silent rewrite).

If a module folder was removed, **do not auto-drop its tables**. `opzhubctl migrate --drop-orphan-modules --yes` is explicit and should run only after `opzhubctl backup`.

## 8. Model download and heavy assets (OCR)

`modules/ocr/scripts/commands/pull-models.py`:

- Downloads pinned **high and medium** detect+rec (+ optional table) into `ocr-models`. The worker loads **one** pack from `python.vision.quality` (auto from RAM/CPU).
- Workers mount that volume read-only.
- If OCR module is absent, the command does not exist; Compose must not declare that volume as required (profile `ocr` in doc 09).

## 9. Folder map of script categories

| Category | Location | Removable with |
| -------- | -------- | -------------- |
| Host bootstrap | `infra/ec2-bootstrap.sh` (spec) | N/A (infra) |
| Compose helpers | `common/scripts` `compose` | Never |
| DB migrate | kernel + each `modules/*/db` | Module folder |
| Seeds | `modules/*/scripts` | Module folder |
| Generators | `common/scripts` `module-gen` | Never |
| Load tests | `modules/*/scripts` or `tools/` | Optional |

## 10. CI usage

Customer package pipeline:

1. Copy kernel + **only** `enabled` ∪ `requires` module folders (`opzhubctl package`). Fail if extra applications are in the tree.
2. `opzhubctl module-gen` (web + Flutter + Java + Python maps) **on the pack**
3. Build images; if `clients.mobile`: `flutter build` `--flavor prod`
4. `opzhubctl doctor --as-prod`
5. `opzhubctl compose up -d` (prod compose file, no dev overlay)
6. `opzhubctl migrate`
7. `opzhubctl health --wait 120`

## 11. Logging and exit codes

- Structured JSON logs with `command`, `module_id`, `correlation_id`.
- Exit `0` success, `2` composition error (missing required module), `3` health fail **or** `status` with a requested service down, `4` migration fail, `5` usage error.

## 12. What must not live in scripts

- Business posting loops that bypass Java validators.
- Hardcoded Postgres or Valkey URLs (read YAML).
- A monolith `scripts/ocr_and_ledger_and_inventory.py`.
