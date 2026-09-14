# Requirement: Backup & Restore

**Module:** `backup-restore` (new)
**DB target:** `opzmain` (job tracking); target company DB for actual pg_dump/restore
**Access level:** Company Admin
**Architecture ref:** [doc 25 §7](../architecture/25-common-features-design.md)

---

## What It Does

Provides on-demand and manual database backup and restore for each company's `opzuser` and `opzhub` databases. The API triggers an async job; the actual `pg_dump` / `pg_restore` runs via a script command (`opzhubctl backup`). Job status and metadata are tracked in `opzmain`.

**Backup flow:**
1. Admin calls `POST /backup-restore/jobs` → creates a `backup_job` row (`PENDING`).
2. The service dispatches the `opzhubctl backup` script with the job ID.
3. The script runs `pg_dump` against the company's DB (connection info from `server_details` in `opzmain`).
4. On completion the script calls `PUT /backup-restore/jobs/{id}/status`.
5. The job row updates to `COMPLETED` (with `file_path` and `file_size_bytes`) or `FAILED` (with `error_message`).

**Restore flow:**
1. Admin calls `POST /backup-restore/restore` with a job `id` (the backup to restore from).
2. A new `backup_job` row is created with `job_type = RESTORE`.
3. The script runs `pg_restore` against the company's DB.
4. Status callback updates the row on completion.

---

## DB Schema

**Table:** `backup_job` — in `opzmain`

| Column | Type | Nullable | Default | Notes |
|--------|------|---------|---------|-------|
| `id` | uuid | No | gen_random_uuid() | PK |
| `company_id` | uuid | No | — | FK → `company_information.id` (RESTRICT delete) |
| `job_type` | text | No | — | `OPZUSER` \| `OPZHUB` \| `FULL` \| `RESTORE` |
| `status` | text | No | `PENDING` | `PENDING` \| `RUNNING` \| `COMPLETED` \| `FAILED` |
| `started_at` | timestamptz | Yes | `null` | Set when script begins |
| `completed_at` | timestamptz | Yes | `null` | Set on terminal status |
| `file_path` | text | Yes | `null` | Absolute path to backup file on server |
| `file_size_bytes` | bigint | Yes | `null` | Set on `COMPLETED` |
| `error_message` | text | Yes | `null` | Safe message only; set on `FAILED` |
| `created_by` | uuid | No | — | User ID who triggered the job |
| `created_at` | timestamptz | No | `now()` | Row creation time |

---

## API Endpoints

Base path: `/api/v1/opzhub/backup-restore`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/jobs` | Trigger a new backup job |
| `POST` | `/jobs/read` | List backup jobs for the company (paginated) |
| `PUT` | `/jobs/{id}/status` | Update job status (called by script — internal) |
| `POST` | `/restore` | Trigger a restore from a completed backup job |

### Create Backup — Request Body

```json
{
  "job_type": "OPZUSER"
}
```

`job_type` options:
- `OPZUSER` — backs up the company's `opzuser` database only.
- `OPZHUB` — backs up the company's `opzhub` database only.
- `FULL` — backs up both in sequence (creates two physical files, tracked as one job).

### Create Backup — Response (201 Created)

```json
{
  "ok": true,
  "data": {
    "id": "9b4e2c8a-...",
    "job_type": "OPZUSER",
    "status": "PENDING",
    "created_at": "2026-09-11T10:00:00Z"
  },
  "correlation_id": "..."
}
```

### Status Callback — Request Body

```json
{
  "status": "COMPLETED",
  "file_path": "/var/lib/opzhub/backups/acme/opzuser_20260911_143000.pgdump",
  "file_size_bytes": 1048576,
  "error_message": null
}
```

The status callback endpoint is **internal only** — exposed to localhost / Docker bridge network, not to the public Nginx listener.

---

## SQL Commands

In `modules/backup-restore/db/commands/`:

| File | SQL purpose |
|------|------------|
| `backup_job.insert.sql` | INSERT new job row, RETURNING id |
| `backup_job.find_by_id.sql` | SELECT by id (for status checks) |
| `backup_job.list_by_company.sql` | SELECT paginated by company_id ORDER BY created_at DESC |
| `backup_job.count_by_company.sql` | COUNT for pagination |
| `backup_job.update_status.sql` | UPDATE status, started_at, completed_at, file_path, file_size_bytes, error_message WHERE id = :id |

---

## Script Commands

In `modules/backup-restore/scripts/commands/`:

**`backup.py`** — wraps `pg_dump`:
- Reads connection info from `server_details` (via API call to the BE on the same host).
- Builds the output file path: `/var/lib/opzhub/backups/<slug>/<db>_<timestamp>.pgdump`.
- On exit: calls `PUT /jobs/{id}/status` with result.

**`restore.py`** — wraps `pg_restore`:
- Reads the file path from the backup job.
- Runs `pg_restore --clean` against the target DB.
- On exit: calls `PUT /jobs/{id}/status` with result.

Both scripts respect the `run-job.sh` wrapper (flock + kill-after timeout) from [doc 16](../architecture/16-jobs-crontab.md).

---

## Files to Create

```
modules/backup-restore/
├── module.yaml                                          ← EXISTS
├── backend/src/main/java/com/managemyopz/modules/backuprestore/
│   ├── BackupRestoreAutoConfiguration.java              ← NEW
│   ├── api/
│   │   ├── BackupController.java                        ← NEW
│   │   └── dto/
│   │       ├── BackupCreateRequest.java                 ← NEW
│   │       ├── BackupJobResponse.java                   ← NEW
│   │       ├── BackupStatusCallbackRequest.java         ← NEW
│   │       └── RestoreRequest.java                      ← NEW
│   ├── application/
│   │   ├── BackupService.java                           ← NEW
│   │   └── RestoreService.java                          ← NEW
│   ├── domain/
│   │   ├── BackupJob.java                               ← NEW (record)
│   │   └── JobStatus.java                               ← NEW (enum)
│   └── data/
│       ├── BackupJobRepository.java                     ← NEW (interface)
│       └── DataClientBackupJobRepository.java           ← NEW (adapter)
├── db/
│   ├── schema/
│   │   └── backup_job.yaml                              ← EXISTS
│   └── commands/
│       ├── backup_job.insert.sql                        ← NEW
│       ├── backup_job.find_by_id.sql                    ← NEW
│       ├── backup_job.list_by_company.sql               ← NEW
│       ├── backup_job.count_by_company.sql              ← NEW
│       └── backup_job.update_status.sql                 ← NEW
├── scripts/
│   ├── cli.yaml                                         ← NEW
│   └── commands/
│       ├── backup.py                                    ← NEW
│       └── restore.py                                   ← NEW
└── frontend/
    ├── index.ts                                         ← NEW
    ├── routes.tsx                                       ← NEW
    └── pages/
        ├── BackupListPage.tsx                           ← NEW
        └── BackupCreatePage.tsx                         ← NEW
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| Only `COMPLETED` jobs can be used as restore source | Service validates status before triggering restore |
| One active job per company at a time | Service checks for `RUNNING` jobs before creating a new one; returns 409 if found |
| `error_message` must not include stack traces or DB connection strings | Script sanitizes before callback |
| Backup files must not be accessible via the public API (file download not in scope v1) | API returns path reference only; no file streaming endpoint |
| `created_by` sourced from session, not request body | Service reads from `SessionAuthentication` |

---

## Dependencies

- `identity` module (session, RBAC).
- `company_information` and `server_details` tables in `opzmain`.
- `opzhubctl` script framework (`run-job.sh`, flock, kill-after).
- No other modules required.

---

## GUI Metadata Design

### Screen: Backup Job List

| Column Key | Heading | Type | Sortable | Role Access |
|-----------|---------|------|---------|-------------|
| `id` | Job ID | `text-truncated` | No | company_admin |
| `job_type` | Type | `badge` | Yes | company_admin |
| `status` | Status | `status-badge` | Yes | company_admin |
| `created_at` | Requested At | `datetime` | Yes (default desc) | company_admin |
| `started_at` | Started At | `datetime` | No | company_admin |
| `completed_at` | Completed At | `datetime` | No | company_admin |
| `file_size_bytes` | Size | `file-size` | No | company_admin |
| — | Actions | `actions` | No | company_admin | View details, Restore (only if `status = COMPLETED`) |

### Screen: Create Backup Form

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `job_type` | Backup Scope | `radio-group` | Yes | `OPZUSER` (User DB), `OPZHUB` (Business DB), `FULL` (Both) | company_admin | Shows a tooltip per option explaining what is included |

### Screen: Job Detail / Status Panel

| Field Key | Heading | Type | Role Access |
|-----------|---------|------|-------------|
| `id` | Job ID | `text-copyable` | company_admin |
| `job_type` | Scope | `badge` | company_admin |
| `status` | Status | `status-badge` | company_admin |
| `started_at` | Started | `datetime` | company_admin |
| `completed_at` | Completed | `datetime` | company_admin |
| `file_path` | Backup File | `text-mono` | company_admin |
| `file_size_bytes` | File Size | `file-size` | company_admin |
| `error_message` | Error | `text-error` | company_admin | Shown only when `status = FAILED` |

### Metadata-Driven Rules

- **Restore** subaction on the list row is visible **only** when `status = COMPLETED`. This is a `visibleWhen: { status: "COMPLETED" }` subaction condition in the field metadata.
- `status` badge colors come from `common/frontend/src/theme/tokens.ts`:
  - `PENDING` → neutral/grey
  - `RUNNING` → info/blue
  - `COMPLETED` → success/green
  - `FAILED` → error/red
- `file_size_bytes` renders via a `file-size` formatter (`1 048 576` → `1.0 MB`) in `common/frontend` — not per-page logic.
- List polls for status changes every **10 seconds** while any row has `status = PENDING` or `RUNNING`. Polling stops when all rows are terminal.

---

## Directory Placement

```
modules/backup-restore/              ← New module folder (non-application-specific admin)
├── backend/
├── scripts/
│   └── commands/
│       ├── backup.py                ← opzhubctl command; invoked via run-job.sh
│       └── restore.py
└── frontend/
    └── pages/
        ├── BackupListPage.tsx
        └── BackupCreatePage.tsx

common/frontend/src/
├── fields/display/
│   ├── FileSizeDisplay.tsx          ← Reusable byte → human-readable formatter
│   ├── StatusBadge.tsx             ← Reusable status badge with color from tokens
│   └── DatetimeDisplay.tsx         ← Reusable locale-aware datetime renderer
└── kernel/constants/
    └── commonConstants.ts           ← STATUS_BADGE_COLORS, FILE_SIZE_UNITS
```

**Rules:**
- `FileSizeDisplay`, `StatusBadge`, and `DatetimeDisplay` are in `common/frontend` — reused across backup-restore, maintenance, and any other module showing these types.
- The polling interval constant lives in the `backup-restore` module constants — not in common (it is domain-specific).
- Script files (`backup.py`, `restore.py`) live inside `modules/backup-restore/scripts/` — not in `common/scripts`. The `opzhubctl` CLI is the common entry point.

---

## Constants

### Backend (`modules/backup-restore/backend/.../BackupRestoreConstants.java`)

```java
public static final String JOB_TYPE_OPZUSER  = "OPZUSER";
public static final String JOB_TYPE_OPZHUB   = "OPZHUB";
public static final String JOB_TYPE_FULL     = "FULL";
public static final String JOB_TYPE_RESTORE  = "RESTORE";
public static final String STATUS_PENDING    = "PENDING";
public static final String STATUS_RUNNING    = "RUNNING";
public static final String STATUS_COMPLETED  = "COMPLETED";
public static final String STATUS_FAILED     = "FAILED";
public static final String BACKUP_FILE_ROOT  = "/var/lib/opzhub/backups";
```

### Frontend (`modules/backup-restore/frontend/backupRestoreConstants.ts`)

```typescript
export const BACKUP_API_BASE        = "/api/v1/opzhub/backup-restore";
export const BACKUP_POLL_INTERVAL_MS = 10_000;
export const JOB_TYPE_OPTIONS = [
  { value: "OPZUSER", label: "User Database (opzuser)"    },
  { value: "OPZHUB",  label: "Business Database (opzhub)" },
  { value: "FULL",    label: "Full Backup (both)"         },
];
export const BACKUP_LIST_HEADING    = "Backup & Restore";
export const CREATE_BACKUP_LABEL    = "Create Backup";
export const RESTORE_LABEL          = "Restore";
export const STATUS_POLL_ENABLED_STATUSES = ["PENDING", "RUNNING"];
```

Status badge colors are in `common/frontend/src/theme/tokens.ts`:

```typescript
export const STATUS_BADGE_COLORS = {
  PENDING:   "var(--color-neutral)",
  RUNNING:   "var(--color-info)",
  COMPLETED: "var(--color-success)",
  FAILED:    "var(--color-error)",
};
```

---

## Optimization, Performance & Memory

### Performance
- Job list paginates server-side — never fetches all rows.
- Status polling uses `setInterval` only when there are active jobs (`PENDING` or `RUNNING`). The interval is cleared immediately when all jobs reach a terminal state — no wasted polling.
- The `pg_dump` and `pg_restore` scripts run **outside** the Spring request thread via `opzhubctl` (async script dispatch). The API responds with `201 PENDING` immediately — no long-running HTTP request.

### Memory
- **React:** The polling `setInterval` ref must be cleared in `useEffect` cleanup. Use a `useRef` to hold the interval ID — not component state — to prevent re-render on clear.
- **Flutter:** If Flutter shows a backup status screen, use a `Timer.periodic` and cancel it in `dispose()`.
- **Backend:** The `BackupJob` domain record is immutable. The service does not hold a list of all jobs in memory — queries are paginated per request.
- **Scripts:** `backup.py` streams `pg_dump` output to disk — it does not buffer the entire dump in memory.

### Optimization
- `backup_job.list_by_company.sql` uses the `idx_backup_job_company_id` and `idx_backup_job_created_at` indexes.
- The status callback (`PUT /jobs/{id}/status`) is a targeted single-row `UPDATE` — no SELECT before UPDATE.
- Completed backup file paths are stored on the server; the API never streams file content — eliminates large response bodies.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/modules/backup-restore/`.
No test files under `modules/backup-restore/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `BackupServiceTest` | Trigger creates PENDING row; duplicate active job → 409; `created_by` from session |
| `RestoreServiceTest` | Non-COMPLETED source → 400; dispatches correct file path |
| `BackupControllerTest` | `@RequiresPermission` enforced — non-admin gets 403 |
| `BackupJobRepositoryTest` | Insert / status update / paginated list queries |

### RBAC in DB

Backup and restore are restricted to `company_admin` role. Migration inserts:

```sql
INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions)
VALUES
  ('company_admin', 'backup-restore', 'bkp', 'vc'),   -- list + trigger backup
  ('company_admin', 'backup-restore', 'rst', 'ca');    -- create restore + approve restore
```

Controller annotations:

```java
@RequiresPermission(module = "backup-restore", feature = "bkp", action = "c")
@PostMapping("/jobs")
public ResponseEntity<ApiEnvelope<BackupJobResponse>> triggerBackup(...) { ... }

@RequiresPermission(module = "backup-restore", feature = "bkp", action = "v")
@PostMapping("/jobs/read")
public ResponseEntity<ApiEnvelope<PagedResult<BackupJobResponse>>> listJobs(...) { ... }

@RequiresPermission(module = "backup-restore", feature = "rst", action = "c")
@PostMapping("/restore")
public ResponseEntity<ApiEnvelope<BackupJobResponse>> triggerRestore(...) { ... }
```

The status callback (`PUT /jobs/{id}/status`) is **internal only** — accessible
from `localhost` / Docker bridge only, not exposed via Nginx. No `@RequiresPermission`
annotation; Nginx `allow 127.0.0.1; deny all;` is the access control.

ABAC: `company_id` for the job is sourced from `SessionAuthentication.getCompanyId()` —
a company admin can only trigger/list jobs for their own company.

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `backup-restore.job.create` | Create Backup form |
| `backup-restore.restore.create` | Trigger Restore form |

```sql
-- form_id: backup-restore.job.create
-- field_key: job_type  field_type: radio  is_mandatory: true  display_order: 1
--   allowed_values: [{"value":"OPZUSER","label":"User Database (opzuser)"},
--                    {"value":"OPZHUB","label":"Business Database (opzhub)"},
--                    {"value":"FULL","label":"Full Backup (both)"}]

-- form_id: backup-restore.restore.create
-- field_key: backup_job_id  field_type: lookup  is_mandatory: true  display_order: 1
--   (lookup endpoint: GET /jobs/read?status=COMPLETED)
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /jobs` | Session | `company_admin` `bkp.c` | `company_id` from session |
| `POST /jobs/read` | Session | `company_admin` `bkp.v` | `company_id` from session |
| `PUT /jobs/{id}/status` | Internal only | None (Nginx block) | Localhost network gate |
| `POST /restore` | Session | `company_admin` `rst.c` | `company_id` from session |

### Coding Standards (this feature)

**Java:** `BackupRestoreConstants.java` holds all status strings, job type strings, and the `BACKUP_FILE_ROOT` path. Scripts must not log DB connection strings or full file paths at DEBUG level.

**Python (`backup.py`, `restore.py`):**
- File header: Organization / Owner / Created at / Description.
- Hardcoded path root and command constants in `constants.py` under `modules/backup-restore/scripts/commands/`.
- DB connection info fetched via internal API call — never from environment variables directly in the script.
- Run `pylint` on both files before commit.

**TypeScript/React:** `STATUS_BADGE_COLORS` in `common/frontend/src/theme/tokens.ts`. `BACKUP_POLL_INTERVAL_MS` and `JOB_TYPE_OPTIONS` in `backupRestoreConstants.ts`. `FileSizeDisplay` imported from `common/frontend`.

### Directory Confirmation

```
modules/backup-restore/
    backend/           ← Java Spring Boot module
    scripts/commands/
        backup.py      ← pg_dump wrapper (pylint enforced)
        restore.py     ← pg_restore wrapper (pylint enforced)
        constants.py   ← script-level constants (one per folder)
    frontend/pages/    ← uses common StatusBadge, FileSizeDisplay
common/frontend/src/fields/display/
    FileSizeDisplay.tsx    ← bytes → human-readable (shared)
    StatusBadge.tsx        ← status badge with token colors (shared)
```
