# 25 — Common Features Design: Company Setup, User Settings, Change Password, Backup & Restore, Maintenance, Login Auto Routing

## 1. Overview

Common features are the backbone of the ManageMyOpz platform and are shared across all company instances, regardless of which licensed application pack is installed. They operate on the centralized **`opzmain`** database (company registry, licensing, routing metadata) and on each company's **`opzuser`** database (user credentials, profile settings).

| Feature | Module | Primary DB | Access Level |
|---------|--------|-----------|--------------|
| Login auto routing | `identity` (extend) | `opzmain` | Public / Pre-auth |
| Company creation & management | `company-setup` (new) | `opzmain` | Super Admin |
| User settings (profile/preferences) | `user-settings` (new) | `opzuser` (per-company) | Authenticated user |
| Change password | `identity` (extend) | `opzuser` (per-company) | Authenticated user |
| Backup & restore | `backup-restore` (new) | `opzmain` (job log) | Company Admin |
| Maintenance mode | `maintenance` (new) | `opzmain` | Super Admin / Company Admin |

---

## 2. Database Architecture

### 2.1 Database Roles

| Database | Owner | Scope | Contents |
|----------|-------|-------|---------|
| `opzmain` | Centralized server | Global | Company registry, licenses, server/endpoint routing, backup jobs, maintenance windows |
| `opzuser` | Per-company server (or hub) | Per-company | Users (`id_user`), user settings, app preferences |
| `opzhub` | Per-company server (or hub) | Per-company | Business application data (HR, finance, inventory, etc.) |

### 2.2 Required Schema Changes in `opzmain`

#### Extend `company_information` (add routing fields)

The current `company_information` table in `opzmain` needs five additional columns to support routing, branding, and status:

| New Column | Type | Constraint | Purpose |
|-----------|------|-----------|---------|
| `company_slug` | text | NOT NULL, UNIQUE | URL-safe primary slug (e.g., `acme`). Used in `acme/user` login format. |
| `routing_mode` | text | NOT NULL, DEFAULT `CENTRAL_DB` | `CENTRAL_DB` \| `COMPANY_BE` \| `SITE` — determines login routing (§3). |
| `endpoint_name` | text | NULLABLE, FK → `backend_endpoint` | Populated **only** when `routing_mode = COMPANY_BE`. Points to the company's BE URL entry. |
| `logo_uri` | text | NULLABLE | Path/URL to the company logo shown on the login screen. |
| `status` | text | NOT NULL, DEFAULT `active` | `active` \| `suspended` \| `expired` — gate for login resolution. |

#### New table: `backend_endpoint` in `opzmain`

Stores the REST base URL and optional inter-service credentials for company-specific backend instances (UseCase 2).

```
backend_endpoint (opzmain)
├── endpoint_name    TEXT  PK                    Natural key (e.g., "acme-be-prod")
├── be_api_url       TEXT  NOT NULL              Base REST URL, e.g., https://acme-be.example.com
├── be_health_url    TEXT  NULLABLE              Health check URL, e.g., /healthz
├── be_api_token     TEXT  NULLABLE              Optional bearer token for hub→company-BE calls
└── is_active        BOOL  NOT NULL DEFAULT true Whether this endpoint is currently accepting traffic
```

> **Security Note:** `be_api_token` is stored in plain text here, the same caveat as `server_details.db_password`. Treat as a known follow-up: encrypt at the application layer or reference a secrets manager before the feature reaches production.

---

## 3. Login Auto Routing — Two Use Cases

### 3.1 Routing Mode Summary

| `routing_mode` | FE Origin | BE Origin | Postgres Connection |
|---------------|-----------|-----------|-------------------|
| `CENTRAL_DB` | Central hub | Central hub | Company-specific DB via `DataRouter` (uses `server_details`) |
| `COMPANY_BE` | Central hub | Company-specific BE (from `backend_endpoint`) | Company BE manages its own DB |
| `SITE` | Company appliance | Company appliance | Local Postgres (router off) |

### 3.2 UseCase 1 — Centralized FE + Centralized BE + Company-Specific DB

**Topology:** `routing_mode = CENTRAL_DB`

```
Browser / Flutter
       │
       │  1. POST /api/v1/opzhub/identity/resolve  {"q":"acme/ada"}
       ▼
Central BE (hub)
       │  2. Look up company_information by slug "acme" in opzmain
       │  3. routing_mode = CENTRAL_DB → use server_details for DB connection
       │  4. Return: { api: "https://hub.example.com", gui: "https://hub.example.com", ... }
       │
       │  5. POST /api/v1/opzhub/identity/login  {"username":"acme/ada", "password":"..."}
       ▼
Central BE (hub) — DataRouter selects company DB
       │
       │  6. DataRouter reads company_id from context
       │  7. Loads server_details (db_ip, db_port, db_username, db_password) from opzmain
       │  8. Opens per-company HikariCP pool → opzuser on company server
       │  9. Verifies Argon2id hash, issues session with company_id
       ▼
Company Postgres (opzuser)
```

**Key Points:**
- The browser stays on the hub origin throughout.
- DB host/credentials are never sent to the browser.
- The kernel `DataRouter` switches the Postgres connection per `company_id` after login.

### 3.3 UseCase 2 — Centralized FE + Company-Specific BE + Company-Specific DB

**Topology:** `routing_mode = COMPANY_BE`

```
Browser / Flutter
       │
       │  1. POST /api/v1/opzhub/identity/resolve  {"q":"acme/ada"}
       ▼
Central BE (hub)
       │  2. Look up company_information by slug "acme" in opzmain
       │  3. routing_mode = COMPANY_BE → read backend_endpoint.be_api_url
       │  4. Return: { api: "https://acme-be.example.com", gui: "https://hub.example.com", ... }
       │
       │  5. FE redirects all further API calls to https://acme-be.example.com
       ▼
Company BE (acme-be.example.com) — standalone instance
       │
       │  6. POST /api/v1/opzhub/identity/login runs on company BE
       │  7. Company BE reads its own DB connection from platform.yaml (or local opzmain)
       │  8. Verifies Argon2id hash, issues session
       ▼
Company Postgres (opzuser on company server)
```

**Key Points:**
- After resolve, the browser switches its `api` base URL to the company's BE.
- The company BE is a full ManageMyOpz runtime (same binary, different `platform.yaml`).
- Central hub does NOT proxy requests to the company BE; the browser connects directly.
- Hub stores only `be_api_url` — never the company DB host — in `backend_endpoint`.

### 3.4 Resolve API Contract

**Endpoint:** `POST /api/v1/opzhub/identity/resolve`
**Authentication:** Not required (public, pre-auth).
**Rate-limiting:** Per IP + company slug prefix. Returns generic error on unknown company (do not enumerate).

**Request:**
```json
{ "q": "acme/ada", "live": true }
```
- `q`: raw login field value (e.g., `acme/ada`, `ada@acme.com`).
- `live`: when `true` the call comes from debounced field input (400 ms); may return cached result.

**Response (success):**
```json
{
  "ok": true,
  "co": "acme",
  "u": "ada",
  "n": "Acme Ltd",
  "logo": "/brand/acme.svg",
  "gui": "https://hub.example.com",
  "api": "https://hub.example.com",
  "st": "ok",
  "skin": "lite"
}
```

- For `CENTRAL_DB`: `api` = hub origin (same as `gui`).
- For `COMPANY_BE`: `api` = `backend_endpoint.be_api_url`.
- For `SITE`: both `gui` and `api` = company appliance origin.
- `st`: `ok` \| `wait` \| `bad`. Never expose `expired` vs `unknown` distinction.

**What must NOT be in the response:**
- `db_host`, DB passwords, full `server_details` row.
- Other tenants' data.
- The entire `backend_endpoint` row (only the URL is provided via `api`).

### 3.5 Company Routing in Identity Module

The following classes are added to `modules/identity/`:

| Class | Package | Role |
|-------|---------|------|
| `CompanyResolveController` | `api/routing/` | Handles `POST /resolve` and `POST /preflight` |
| `CompanyRoutingService` | `application/routing/` | Resolves slug → company row, builds response, validates status |
| `RoutingMode` | `application/routing/` | Enum: `CENTRAL_DB`, `COMPANY_BE`, `SITE` |
| `ResolveRequest` | `api/routing/dto/` | DTO: `q`, `live` |
| `ResolveResponse` | `api/routing/dto/` | DTO: `ok`, `co`, `u`, `n`, `logo`, `gui`, `api`, `st`, `skin` |
| `CompanyInformationRepository` | `data/routing/` | Queries `company_information` JOIN `backend_endpoint` from `opzmain` |

**SQL commands added to `modules/identity/db/commands/`:**
- `identity.resolve_company_by_slug.sql` — finds company by slug in `company_information` + joins `backend_endpoint` if needed.
- `identity.resolve_company_by_reference.sql` — resolves via `company_references` JSONB for email-domain lookups.

---

## 4. Module: `company-setup` (New)

Provides the admin UI and API for creating and managing companies, server connection details, and backend endpoint entries in `opzmain`.

### 4.1 Directory Structure

```
modules/company-setup/
├── module.yaml
├── backend/
│   └── src/main/java/com/managemyopz/modules/companysetup/
│       ├── CompanySetupAutoConfiguration.java
│       ├── api/
│       │   ├── CompanyController.java             POST/GET/PUT/DELETE /api/v1/opzhub/company-setup/companies
│       │   ├── ServerDetailsController.java        POST/GET/PUT/DELETE /api/v1/opzhub/company-setup/server-details
│       │   ├── BackendEndpointController.java      POST/GET/PUT/DELETE /api/v1/opzhub/company-setup/backend-endpoints
│       │   └── dto/
│       │       ├── CompanyCreateRequest.java
│       │       ├── CompanyUpdateRequest.java
│       │       ├── CompanyResponse.java
│       │       ├── ServerDetailsRequest.java
│       │       ├── ServerDetailsResponse.java
│       │       ├── BackendEndpointRequest.java
│       │       └── BackendEndpointResponse.java
│       ├── application/
│       │   ├── CompanyService.java
│       │   └── ServerDetailsService.java
│       ├── domain/
│       │   ├── Company.java
│       │   ├── ServerDetails.java
│       │   └── BackendEndpoint.java
│       └── data/
│           ├── CompanyRepository.java              (interface)
│           ├── DataClientCompanyRepository.java    (adapter)
│           ├── ServerDetailsRepository.java        (interface)
│           └── DataClientServerDetailsRepository.java
├── db/
│   ├── schema/
│   │   └── (schema changes tracked in licensing module — see §2.2)
│   └── commands/
│       ├── company.insert.sql
│       ├── company.find_by_id.sql
│       ├── company.find_by_slug.sql
│       ├── company.list_paged.sql
│       ├── company.count_total.sql
│       ├── company.update.sql
│       ├── company.delete.sql
│       ├── server_details.insert.sql
│       ├── server_details.find_by_name.sql
│       ├── server_details.list_all.sql
│       ├── server_details.update.sql
│       ├── server_details.delete.sql
│       ├── backend_endpoint.insert.sql
│       ├── backend_endpoint.find_by_name.sql
│       ├── backend_endpoint.list_all.sql
│       ├── backend_endpoint.update.sql
│       └── backend_endpoint.delete.sql
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   └── pages/
│       ├── CompanyListPage.tsx
│       ├── CompanyCreatePage.tsx
│       ├── CompanyEditPage.tsx
│       ├── ServerDetailsListPage.tsx
│       ├── ServerDetailsFormPage.tsx
│       ├── BackendEndpointListPage.tsx
│       └── BackendEndpointFormPage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        └── company_list_page.dart
```

### 4.2 API Endpoints

All endpoints require `super_admin` role.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/opzhub/company-setup/companies` | Create company |
| `POST` | `/api/v1/opzhub/company-setup/companies/read` | List / read companies |
| `PUT` | `/api/v1/opzhub/company-setup/companies` | Update company |
| `DELETE` | `/api/v1/opzhub/company-setup/companies` | Delete company |
| `POST` | `/api/v1/opzhub/company-setup/server-details` | Create server entry |
| `POST` | `/api/v1/opzhub/company-setup/server-details/read` | List server entries |
| `PUT` | `/api/v1/opzhub/company-setup/server-details` | Update server entry |
| `DELETE` | `/api/v1/opzhub/company-setup/server-details` | Delete server entry |
| `POST` | `/api/v1/opzhub/company-setup/backend-endpoints` | Create BE endpoint |
| `POST` | `/api/v1/opzhub/company-setup/backend-endpoints/read` | List BE endpoints |
| `PUT` | `/api/v1/opzhub/company-setup/backend-endpoints` | Update BE endpoint |
| `DELETE` | `/api/v1/opzhub/company-setup/backend-endpoints` | Delete BE endpoint |

### 4.3 Company Create Flow

1. Admin creates `server_details` entry (DB connection for the company's server).
2. If `COMPANY_BE` routing: Admin creates `backend_endpoint` entry (company's BE URL).
3. Admin creates `company_information` referencing the above + a `company_license`.
4. Setting `routing_mode = COMPANY_BE` requires a non-null `endpoint_name`.
5. Setting `routing_mode = CENTRAL_DB` requires a non-null `server_name`.

**Validation rules:**
- `company_slug`: must match `^[a-z0-9][a-z0-9-]{1,63}$`. Unique in `company_information`.
- `routing_mode = COMPANY_BE` + `endpoint_name = null` → 400 Bad Request.
- `routing_mode = CENTRAL_DB` + `server_name = null` → 400 Bad Request.
- `license_code` must exist and not be expired at creation time.

---

## 5. Module: `user-settings` (New)

Manages user profile preferences stored in the company's `opzuser` database.

### 5.1 Directory Structure

```
modules/user-settings/
├── module.yaml
├── backend/
│   └── src/main/java/com/managemyopz/modules/usersettings/
│       ├── UserSettingsAutoConfiguration.java
│       ├── api/
│       │   ├── UserSettingsController.java         GET/PUT /api/v1/opzhub/user-settings/profile
│       │   └── dto/
│       │       ├── UserSettingsResponse.java
│       │       └── UserSettingsUpdateRequest.java
│       ├── application/
│       │   └── UserSettingsService.java
│       ├── domain/
│       │   └── UserSettings.java
│       └── data/
│           ├── UserSettingsRepository.java
│           └── DataClientUserSettingsRepository.java
├── db/
│   ├── schema/
│   │   └── user_settings.yaml                      (opzuser — per-company)
│   └── commands/
│       ├── user_settings.find_by_user_id.sql
│       └── user_settings.upsert.sql
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   └── pages/
│       └── UserSettingsPage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        └── user_settings_page.dart
```

### 5.2 `user_settings` Table (in `opzuser`)

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| `user_id` | uuid | PK, FK → `id_user.id` | Ties to the user row |
| `preferred_language` | text | NULLABLE | ISO 639-1 code (e.g., `en`, `ta`) |
| `timezone` | text | NULLABLE | IANA timezone name (e.g., `Asia/Kolkata`) |
| `theme` | text | NULLABLE | `lite` \| `rich` \| `system` |
| `email_notifications_enabled` | boolean | NOT NULL, DEFAULT true | Toggle email alerts |
| `updated_at` | timestamptz | NOT NULL, DEFAULT now() | Last updated timestamp |

### 5.3 API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/opzhub/user-settings/profile` | Fetch current user's settings |
| `PUT` | `/api/v1/opzhub/user-settings/profile` | Update current user's settings (upsert) |

Session's `user_id` from `SessionAuthentication` is used — no ID in the request body.

---

## 6. Change Password (Identity Module Extension)

Change password is an extension of the `identity` module, not a separate module. It modifies `id_user.password_hash` in the company's `opzuser`.

### 6.1 New Classes in `modules/identity/`

| Class | Package | Role |
|-------|---------|------|
| `ChangePasswordController` | `api/` | `PUT /api/v1/opzhub/identity/change-password` |
| `ChangePasswordService` | `application/` | Validates old password, hashes new, updates DB |
| `ChangePasswordRequest` | `api/dto/` | `{ "current_password": "...", "new_password": "...", "confirm_password": "..." }` |

### 6.2 API Contract

**Endpoint:** `PUT /api/v1/opzhub/identity/change-password`
**Authentication:** Required.

**Request:**
```json
{
  "current_password": "OldSecret!1",
  "new_password": "NewSecret!2",
  "confirm_password": "NewSecret!2"
}
```

**Validation:**
- `current_password`: must match stored Argon2id hash.
- `new_password`: minimum 8 characters; cannot equal `current_password`.
- `confirm_password`: must equal `new_password`.

**Success:** `200 OK` — `ApiEnvelope<Void>` with `ok: true`.
**Failures:** `400` (validation), `401` (wrong current password).

**SQL command added to `modules/identity/db/commands/`:**
- `identity.update_password_hash.sql` — `UPDATE id_user SET password_hash = :hash WHERE id = :user_id`

---

## 7. Module: `backup-restore` (New)

Provides scheduled and on-demand database backup/restore for each company's `opzuser` and `opzhub` databases. Backup jobs are tracked in `opzmain`.

### 7.1 Directory Structure

```
modules/backup-restore/
├── module.yaml
├── backend/
│   └── src/main/java/com/managemyopz/modules/backuprestore/
│       ├── BackupRestoreAutoConfiguration.java
│       ├── api/
│       │   ├── BackupController.java              POST/GET /api/v1/opzhub/backup-restore/jobs
│       │   └── dto/
│       │       ├── BackupCreateRequest.java
│       │       ├── BackupJobResponse.java
│       │       └── RestoreRequest.java
│       ├── application/
│       │   ├── BackupService.java                 Triggers pg_dump job, tracks status
│       │   └── RestoreService.java                Triggers pg_restore job
│       ├── domain/
│       │   ├── BackupJob.java
│       │   └── JobStatus.java                     Enum: PENDING, RUNNING, COMPLETED, FAILED
│       └── data/
│           ├── BackupJobRepository.java
│           └── DataClientBackupJobRepository.java
├── db/
│   ├── schema/
│   │   └── backup_job.yaml                       (opzmain — centralized tracking)
│   └── commands/
│       ├── backup_job.insert.sql
│       ├── backup_job.find_by_id.sql
│       ├── backup_job.list_by_company.sql
│       ├── backup_job.count_by_company.sql
│       └── backup_job.update_status.sql
├── scripts/
│   ├── cli.yaml
│   └── commands/
│       ├── backup.py                              pg_dump wrapper via opzhubctl
│       └── restore.py                             pg_restore wrapper via opzhubctl
└── frontend/
    ├── index.ts
    ├── routes.tsx
    └── pages/
        ├── BackupListPage.tsx
        └── BackupCreatePage.tsx
```

### 7.2 `backup_job` Table (in `opzmain`)

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| `id` | uuid | PK, DEFAULT gen_random_uuid() | Job identifier |
| `company_id` | uuid | NOT NULL, FK → `company_information.id` | Which company's DB is being backed up |
| `job_type` | text | NOT NULL | `OPZUSER` \| `OPZHUB` \| `FULL` |
| `status` | text | NOT NULL, DEFAULT `PENDING` | `PENDING` \| `RUNNING` \| `COMPLETED` \| `FAILED` |
| `started_at` | timestamptz | NULLABLE | When the job actually started |
| `completed_at` | timestamptz | NULLABLE | When it finished |
| `file_path` | text | NULLABLE | Backup file path on the server |
| `file_size_bytes` | bigint | NULLABLE | Compressed size of the backup file |
| `error_message` | text | NULLABLE | Set on `FAILED` — safe message only, no stack trace |
| `created_by` | uuid | NOT NULL | User ID who triggered the backup |
| `created_at` | timestamptz | NOT NULL, DEFAULT now() | Job creation time |

### 7.3 Backup Execution Model

1. API call creates a `backup_job` row with `status = PENDING`.
2. The `BackupService` delegates to the `opzhubctl backup` script command (async).
3. The script runs `pg_dump` against the company's DB (connection info from `server_details` in `opzmain`).
4. On success/failure: script calls back to `PUT /api/v1/opzhub/backup-restore/jobs/{id}/status`.
5. The `backup_job` row is updated accordingly.

### 7.4 API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/opzhub/backup-restore/jobs` | Trigger a new backup job |
| `POST` | `/api/v1/opzhub/backup-restore/jobs/read` | List backup jobs (paginated) |
| `PUT` | `/api/v1/opzhub/backup-restore/jobs/{id}/status` | Update job status (internal/script use) |
| `POST` | `/api/v1/opzhub/backup-restore/restore` | Trigger restore from a backup file |

---

## 8. Module: `maintenance` (New)

Provides maintenance mode control and system health information. A maintenance window disables non-admin access to the application and shows a maintenance page.

### 8.1 Directory Structure

```
modules/maintenance/
├── module.yaml
├── backend/
│   └── src/main/java/com/managemyopz/modules/maintenance/
│       ├── MaintenanceAutoConfiguration.java
│       ├── api/
│       │   ├── MaintenanceController.java          GET/POST/DELETE /api/v1/opzhub/maintenance/windows
│       │   └── dto/
│       │       ├── MaintenanceWindowRequest.java
│       │       └── MaintenanceWindowResponse.java
│       ├── application/
│       │   └── MaintenanceService.java
│       ├── domain/
│       │   └── MaintenanceWindow.java
│       └── data/
│           ├── MaintenanceWindowRepository.java
│           └── DataClientMaintenanceRepository.java
├── db/
│   ├── schema/
│   │   └── maintenance_window.yaml               (opzmain)
│   └── commands/
│       ├── maintenance.insert.sql
│       ├── maintenance.find_active_by_company.sql
│       ├── maintenance.find_global_active.sql
│       ├── maintenance.list_paged.sql
│       └── maintenance.deactivate.sql
└── frontend/
    ├── index.ts
    ├── routes.tsx
    └── pages/
        ├── MaintenanceListPage.tsx
        ├── MaintenanceCreatePage.tsx
        └── MaintenanceBannerWidget.tsx            Injected into AppShell when active
```

### 8.2 `maintenance_window` Table (in `opzmain`)

| Column | Type | Constraint | Purpose |
|--------|------|-----------|---------|
| `id` | uuid | PK, DEFAULT gen_random_uuid() | Window identifier |
| `company_id` | uuid | NULLABLE | `null` = platform-wide; non-null = single company |
| `is_active` | boolean | NOT NULL, DEFAULT true | Whether this window is currently in effect |
| `reason` | text | NULLABLE | Human-readable message shown to users |
| `starts_at` | timestamptz | NOT NULL, DEFAULT now() | When maintenance begins |
| `ends_at` | timestamptz | NULLABLE | Planned end time (`null` = open-ended) |
| `created_by` | uuid | NOT NULL | Super admin user ID |
| `created_at` | timestamptz | NOT NULL, DEFAULT now() | Row creation time |

### 8.3 Maintenance Filter

The kernel adds a `MaintenanceFilter` (registered only when the `maintenance` module is present) that:
1. Checks the cache for an active maintenance window on every inbound request.
2. Skips if the user has the `super_admin` role (admins can still operate).
3. Returns `HTTP 503 Service Unavailable` with body `{ "ok": false, "error": { "code": "maintenance", ... } }` when active.
4. Cache key: `maintenance:active:<company_id>` — short TTL (30 s).

### 8.4 API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/opzhub/maintenance/windows` | Create a maintenance window |
| `POST` | `/api/v1/opzhub/maintenance/windows/read` | List windows (paginated) |
| `DELETE` | `/api/v1/opzhub/maintenance/windows/{id}` | Deactivate (end) a window |
| `GET` | `/api/v1/opzhub/maintenance/windows/active` | Check if currently in maintenance |

---

## 9. Full Directory Structure

The complete tree showing all new and extended paths:

```
modules/
│
├── identity/                              ← EXTEND (add routing + change-password)
│   ├── module.yaml                        (update: add /resolve route + change-password route)
│   ├── backend/src/main/java/com/managemyopz/modules/identity/
│   │   ├── api/
│   │   │   ├── IdentityController.java    (existing — login/session/logout)
│   │   │   ├── ChangePasswordController.java            ← NEW
│   │   │   └── routing/
│   │   │       └── CompanyResolveController.java        ← NEW  /resolve, /preflight
│   │   ├── application/
│   │   │   ├── AuthService.java           (existing)
│   │   │   ├── ChangePasswordService.java               ← NEW
│   │   │   └── routing/
│   │   │       ├── CompanyRoutingService.java            ← NEW
│   │   │       └── RoutingMode.java                     ← NEW  enum
│   │   ├── data/
│   │   │   ├── DataClientIdentityRepository.java (existing)
│   │   │   └── routing/
│   │   │       └── CompanyInformationRepository.java    ← NEW
│   │   └── domain/
│   │       └── User.java                  (existing)
│   └── db/
│       └── commands/
│           ├── identity.find_user_by_username.sql        (existing)
│           ├── identity.find_user_by_email.sql           (existing)
│           ├── identity.update_password_hash.sql         ← NEW
│           ├── identity.resolve_company_by_slug.sql      ← NEW
│           └── identity.resolve_company_by_reference.sql ← NEW
│
├── company-setup/                         ← NEW MODULE
│   ├── module.yaml
│   ├── backend/src/main/java/com/managemyopz/modules/companysetup/
│   │   ├── CompanySetupAutoConfiguration.java
│   │   ├── api/
│   │   │   ├── CompanyController.java
│   │   │   ├── ServerDetailsController.java
│   │   │   ├── BackendEndpointController.java
│   │   │   └── dto/
│   │   │       ├── CompanyCreateRequest.java
│   │   │       ├── CompanyUpdateRequest.java
│   │   │       ├── CompanyResponse.java
│   │   │       ├── ServerDetailsRequest.java
│   │   │       ├── ServerDetailsResponse.java
│   │   │       ├── BackendEndpointRequest.java
│   │   │       └── BackendEndpointResponse.java
│   │   ├── application/
│   │   │   ├── CompanyService.java
│   │   │   └── ServerDetailsService.java
│   │   ├── domain/
│   │   │   ├── Company.java
│   │   │   ├── ServerDetails.java
│   │   │   └── BackendEndpoint.java
│   │   └── data/
│   │       ├── CompanyRepository.java
│   │       ├── DataClientCompanyRepository.java
│   │       ├── ServerDetailsRepository.java
│   │       └── DataClientServerDetailsRepository.java
│   ├── db/
│   │   └── commands/
│   │       ├── company.insert.sql
│   │       ├── company.find_by_id.sql
│   │       ├── company.find_by_slug.sql
│   │       ├── company.list_paged.sql
│   │       ├── company.count_total.sql
│   │       ├── company.update.sql
│   │       ├── company.delete.sql
│   │       ├── server_details.insert.sql
│   │       ├── server_details.find_by_name.sql
│   │       ├── server_details.list_all.sql
│   │       ├── server_details.update.sql
│   │       ├── server_details.delete.sql
│   │       ├── backend_endpoint.insert.sql
│   │       ├── backend_endpoint.find_by_name.sql
│   │       ├── backend_endpoint.list_all.sql
│   │       ├── backend_endpoint.update.sql
│   │       └── backend_endpoint.delete.sql
│   ├── frontend/
│   │   ├── index.ts
│   │   ├── routes.tsx
│   │   └── pages/
│   │       ├── CompanyListPage.tsx
│   │       ├── CompanyCreatePage.tsx
│   │       ├── CompanyEditPage.tsx
│   │       ├── ServerDetailsListPage.tsx
│   │       ├── ServerDetailsFormPage.tsx
│   │       ├── BackendEndpointListPage.tsx
│   │       └── BackendEndpointFormPage.tsx
│   └── mobile/
│       ├── plugin.dart
│       └── pages/
│           └── company_list_page.dart
│
├── user-settings/                         ← NEW MODULE
│   ├── module.yaml
│   ├── backend/src/main/java/com/managemyopz/modules/usersettings/
│   │   ├── UserSettingsAutoConfiguration.java
│   │   ├── api/
│   │   │   ├── UserSettingsController.java
│   │   │   └── dto/
│   │   │       ├── UserSettingsResponse.java
│   │   │       └── UserSettingsUpdateRequest.java
│   │   ├── application/
│   │   │   └── UserSettingsService.java
│   │   ├── domain/
│   │   │   └── UserSettings.java
│   │   └── data/
│   │       ├── UserSettingsRepository.java
│   │       └── DataClientUserSettingsRepository.java
│   ├── db/
│   │   ├── schema/
│   │   │   └── user_settings.yaml         (opzuser — per-company)
│   │   └── commands/
│   │       ├── user_settings.find_by_user_id.sql
│   │       └── user_settings.upsert.sql
│   ├── frontend/
│   │   ├── index.ts
│   │   ├── routes.tsx
│   │   └── pages/
│   │       └── UserSettingsPage.tsx
│   └── mobile/
│       ├── plugin.dart
│       └── pages/
│           └── user_settings_page.dart
│
├── backup-restore/                        ← NEW MODULE
│   ├── module.yaml
│   ├── backend/src/main/java/com/managemyopz/modules/backuprestore/
│   │   ├── BackupRestoreAutoConfiguration.java
│   │   ├── api/
│   │   │   ├── BackupController.java
│   │   │   └── dto/
│   │   │       ├── BackupCreateRequest.java
│   │   │       ├── BackupJobResponse.java
│   │   │       └── RestoreRequest.java
│   │   ├── application/
│   │   │   ├── BackupService.java
│   │   │   └── RestoreService.java
│   │   ├── domain/
│   │   │   ├── BackupJob.java
│   │   │   └── JobStatus.java             (enum)
│   │   └── data/
│   │       ├── BackupJobRepository.java
│   │       └── DataClientBackupJobRepository.java
│   ├── db/
│   │   ├── schema/
│   │   │   └── backup_job.yaml            (opzmain)
│   │   └── commands/
│   │       ├── backup_job.insert.sql
│   │       ├── backup_job.find_by_id.sql
│   │       ├── backup_job.list_by_company.sql
│   │       ├── backup_job.count_by_company.sql
│   │       └── backup_job.update_status.sql
│   ├── scripts/
│   │   ├── cli.yaml
│   │   └── commands/
│   │       ├── backup.py
│   │       └── restore.py
│   └── frontend/
│       ├── index.ts
│       ├── routes.tsx
│       └── pages/
│           ├── BackupListPage.tsx
│           └── BackupCreatePage.tsx
│
└── maintenance/                           ← NEW MODULE
    ├── module.yaml
    ├── backend/src/main/java/com/managemyopz/modules/maintenance/
    │   ├── MaintenanceAutoConfiguration.java
    │   ├── api/
    │   │   ├── MaintenanceController.java
    │   │   └── dto/
    │   │       ├── MaintenanceWindowRequest.java
    │   │       └── MaintenanceWindowResponse.java
    │   ├── application/
    │   │   └── MaintenanceService.java
    │   ├── domain/
    │   │   └── MaintenanceWindow.java
    │   └── data/
    │       ├── MaintenanceWindowRepository.java
    │       └── DataClientMaintenanceRepository.java
    ├── db/
    │   ├── schema/
    │   │   └── maintenance_window.yaml    (opzmain)
    │   └── commands/
    │       ├── maintenance.insert.sql
    │       ├── maintenance.find_active_by_company.sql
    │       ├── maintenance.find_global_active.sql
    │       ├── maintenance.list_paged.sql
    │       └── maintenance.deactivate.sql
    └── frontend/
        ├── index.ts
        ├── routes.tsx
        └── pages/
            ├── MaintenanceListPage.tsx
            ├── MaintenanceCreatePage.tsx
            └── MaintenanceBannerWidget.tsx
```

---

## 10. DB Schema Extension Files

### 10.1 `modules/licensing/db/schema/company_information.yaml` (extended)

Version bumped to `2`. Adds `company_slug`, `routing_mode`, `endpoint_name`, `logo_uri`, `status` columns plus a new unique index on slug and a new FK to `backend_endpoint`.

### 10.2 `modules/licensing/db/schema/backend_endpoint.yaml` (new)

Schema placed in the `licensing` module since it lives in `opzmain` alongside the other registry tables. CRUD operations are in the `company-setup` module (clean separation of schema ownership from access logic).

### 10.3 `modules/user-settings/db/schema/user_settings.yaml` (new)

Placed in the `user-settings` module. Database target: `OPZUSER` (deployed against each company's `opzuser` database).

### 10.4 `modules/backup-restore/db/schema/backup_job.yaml` (new)

Placed in the `backup-restore` module. Database target: `OPZMAIN` (centralized job tracking).

### 10.5 `modules/maintenance/db/schema/maintenance_window.yaml` (new)

Placed in the `maintenance` module. Database target: `OPZMAIN`.

---

## 11. Frontend Routing Logic

The frontend implements the two-step login flow:

### Step 1 — Company resolve (on login field input, debounced 400 ms)

```typescript
// Triggered when company slug is ≥ 2 chars in the login field
const resolveResult = await identityApi.resolve({ q: loginInput, live: true });
if (resolveResult.ok && resolveResult.st === "ok") {
  setCompanyBranding(resolveResult);   // update logo, name, skin
  setApiOrigin(resolveResult.api);     // store company BE URL
}
```

### Step 2 — Login submission

```typescript
// Uses the api origin returned by resolve
const loginResponse = await httpClient.post(
  `${apiOrigin}/api/v1/opzhub/identity/login`,
  { username: loginInput, password: passwordInput }
);
```

For **UseCase 1** (`CENTRAL_DB`): `apiOrigin` = hub origin → same BE handles login.
For **UseCase 2** (`COMPANY_BE`): `apiOrigin` = company BE URL → company BE handles login.

The `apiOrigin` is persisted in the session store for subsequent API calls.

**Flutter equivalent:**
```dart
// Stored in the session provider
final String apiOrigin = resolveResponse.api;
// All subsequent http calls use this base URL
```

---

## 12. Mobile (Flutter) Routing

The Flutter app follows the same resolve-then-login flow:

1. `login_page.dart` debounces the identifier field and calls `POST /api/v1/opzhub/identity/resolve`.
2. On success: updates the `CompanyBrandingNotifier` (logo, name).
3. The resolved `api` origin is stored in the `SessionProvider` as `apiBaseUrl`.
4. `identity_api.dart` uses `apiBaseUrl` for all subsequent requests.

New Flutter files added to `modules/identity/mobile/`:

```
modules/identity/mobile/
├── plugin.dart                           (existing)
└── pages/
    ├── login_page.dart                   (existing — extend with resolve flow)
    └── change_password_page.dart         ← NEW
```

---

## 13. Module Registration Summary

| Module | `requires` | Routes | API Prefixes | Migrations |
|--------|-----------|--------|-------------|-----------|
| `identity` (ext) | — | `/login`, `/change-password` | `/api/v1/opzhub/identity` | backend: true |
| `company-setup` | `identity` | `/admin/company-setup` | `/api/v1/opzhub/company-setup` | backend: true |
| `user-settings` | `identity` | `/settings/profile` | `/api/v1/opzhub/user-settings` | backend: true |
| `backup-restore` | `identity` | `/admin/backup-restore` | `/api/v1/opzhub/backup-restore` | backend: true |
| `maintenance` | `identity` | `/admin/maintenance` | `/api/v1/opzhub/maintenance` | backend: true |

---

## 14. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| DB credentials in `server_details` | Plain text — follow-up: encrypt at application layer before persist |
| BE token in `backend_endpoint` | Plain text — same follow-up as above |
| Resolve endpoint enumeration | Returns generic error for unknown/unlicensed companies. Rate-limited per IP + slug prefix |
| `be_api_url` in resolve response | Only the URL is returned, never DB host, credentials, or full `backend_endpoint` row |
| Password change | Old password must be verified before update. Argon2id used for new hash |
| Backup file access | Files stored on server; API returns only path references, not file content |
| Maintenance bypass | Only `super_admin` role can bypass maintenance filter |
| Cross-company data leakage | `company_id` from session gates all DB queries; `DataRouter` enforces per-company pool |

---

## 15. Implementation Order (Recommended)

1. **DB schema extensions** — Extend `company_information`, add `backend_endpoint` in `opzmain`.
2. **Routing (identity)** — `CompanyResolveController` + `CompanyRoutingService` + SQL commands.
3. **Company setup** — `company-setup` module with full CRUD for companies, server details, and BE endpoints.
4. **Change password** — Extend `identity` module with `ChangePasswordController` + service.
5. **User settings** — `user-settings` module with `user_settings` table in `opzuser`.
6. **Backup & restore** — `backup-restore` module with `backup_job` table + script wrappers.
7. **Maintenance** — `maintenance` module with `maintenance_window` table + kernel filter.

---

*Related docs: [20 — Licensing, site vs central deploy](20-licensing-site-central.md), [07 — Data & Cache client/server](07-data-cache-client-server.md), [18 — Identity, RBAC, ABAC, OAuth2](18-identity-rbac-abac-oauth2.md)*
