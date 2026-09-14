# 19 — Database backup, recovery, and schema migration

Postgres is the system of record. Application upgrades replace `/home/tsuser/opzhub` only. **Data, dumps, and migration history stay in Docker volumes + `/var/lib/opzhub/backup`.** Schema changes are versioned migrations that **do not wipe customer rows** on install or upgrade.

## 1. Backup (hourly)

Default interval: **every 1 hour** (`0 * * * *` as `tsuser` via `opzhub-run-cron db-backup`). Change only in crontab / YAML `backup.interval_cron`; do not start a second dump if the previous hour is still running (`flock` exit 75).

### 1.1 Destinations (configuration)

`backup.targets` is a list. At least one must be on when `backup.enabled: true`.

| Target | Where | Survives app upgrade? |
| ------ | ----- | --------------------- |
| `local` | `/var/lib/opzhub/backup/db/` | Yes (not in the release zip) |
| `ftp` | External FTP/FTPS/SFTP host | Off-box copy |

Both may be enabled together: write local, then **upload the same file**. Local-only, FTP-only, or both are valid.

Prod: **FTPS** (`ftp` + TLS) or **SFTP**. Plain FTP only if `profile: dev` and `backup.ftp.tls: false`. Credentials in `/etc/opzhub/secrets.env`, never in the upgrade tarball.

```yaml
backup:
  enabled: true
  interval_cron: "0 * * * *"      # hourly
  format: custom                  # pg_dump -Fc
  local:
    enabled: true
    dir: /var/lib/opzhub/backup/db
    keep: 48                      # hourly files to retain
  ftp:
    enabled: false                # set true to ship off-box
    protocol: ftps                # ftps | sftp | ftp (ftp forbidden in prod)
    host: ftp.example.com
    port: 21
    user: ${BACKUP_FTP_USER}
    password: ${BACKUP_FTP_PASSWORD}
    dir: /opzhub/${SOLUTION_ID}
    tls: true
    keep: 168                     # remote files to retain if the protocol allows delete
  valkey:
    enabled: false                # optional RDB snapshot; default off (cache)
```

File name: `opzhub-{solution}-{UTC}.dump` plus `.sha256`. Job log: `/var/log/opzhub/jobs/db-backup.log`.

Dump runs as **tsuser** (typically `docker exec` into `postgres` as the app role). Timeout via `run-job.sh` (default 1 hour, `--kill-after`).

### 1.2 Recovery

```
opzhubctl restore --file /var/lib/opzhub/backup/db/opzhub-….dump
```

1. Verify checksum.
2. `opzhubctl stop` (apps; Postgres stays up).
3. `pg_restore --clean --if-exists` into the app database (**not** a silent drop of the cluster).
4. `opzhubctl migrate` so code newer than the dump still applies **forward** migrations.
5. `opzhubctl start`.

Do not restore onto a live `opzbe` process. Point-in-time recovery beyond dump files is out of v1 (WAL archive can be added later without changing the hourly job).

## 2. Schema migration (install, upgrade, no data wipe)

### 2.1 When it runs

| Event | What |
| ----- | ---- |
| First start after install | Empty DB → **create** kernel + **present** module schemas/tables |
| Start after code upgrade | Apply **only new** versions; existing rows stay |
| `opzhubctl migrate` | Same runner; idempotent (safe to run twice) |
| `opzhubctl start` | Runs migrate **before** bringing `opzbe` up (`migrate_on_start: true`, default) |

`db.type: memory` (tests) uses an in-memory migrator; prod is Postgres.

### 2.2 Versioned files (expand / contract)

```
common/backend/db/
  0001_kernel_init.sql
modules/<id>/db/
  0001_<id>_init.sql
  0002_<id>_add_emp_email.sql
  rollback/                      # optional; not auto-run
```

Each file is one version. Recorded in `kernel_schema_migrations (module_id, version, checksum, applied_at)`.

| Change | Automatic migrate |
| ------ | ----------------- |
| CREATE SCHEMA / TABLE / INDEX | Yes |
| ADD COLUMN (nullable or with default) | Yes |
| Backfill then SET NOT NULL | Yes (two versions) |
| DROP COLUMN / TABLE | Only a **later numbered** file that does not `TRUNCATE` business data; prefer expand/contract |
| DROP another module’s schema | **Never** automatic |

**Existing data:** migrate must not `DROP DATABASE`, `TRUNCATE` app tables, or `DELETE FROM` without a versioned, reviewed file. Checksum mismatch of an **already applied** file → **fail** (do not rewrite history).

Removed application folder: tables **remain** until `opzhubctl migrate --drop-orphan-modules --yes` (after a backup). An HR-only pack never created `tkt_*`, so there is nothing to drop.

### 2.3 Order

1. Kernel migrations.
2. Present modules sorted by `requires`.
3. Skip versions already in `kernel_schema_migrations`.
4. Fail closed on SQL error; do not start `opzbe` if `migrate_on_start` failed.

Seeds are **not** migrations (`opzhubctl <app> seed` only).

## 3. Operator commands

| Command | Role |
| ------- | ---- |
| `opzhubctl migrate` | Apply pending versions |
| `opzhubctl migrate --status` | What is applied vs on disk |
| `opzhubctl migrate --drop-orphan-modules --yes` | Dangerous drop of absent modules’ schemas |
| `opzhubctl backup` | Same as cron job (manual) |
| `opzhubctl restore --file PATH` | Recovery |

Cron: `/usr/local/bin/opzhub-run-cron db-backup` hourly ([16](16-jobs-crontab.md)). Implementation: `infra/wrappers/jobs/db-backup.sh`.

## 4. What must not happen

- Dumps under `/home/tsuser/opzhub` (wiped on upgrade).
- Overlapping hourly dumps (hang / lock).
- Auto-migrate that deletes customer rows.
- Plain FTP in `profile: prod`.
- Restore without a prior backup of the current DB.
