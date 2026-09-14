# Requirement: Maintenance Mode

**Module:** `maintenance` (new)
**DB target:** `opzmain`
**Access level:** Super Admin (create/end windows); Authenticated users (read active status)
**Architecture ref:** [doc 25 §8](../architecture/25-common-features-design.md)

---

## What It Does

Provides the ability to put the platform (or a single company) into **maintenance mode**. While a maintenance window is active:

- Non-admin API requests return **HTTP 503 Service Unavailable** with a JSON error body.
- The frontend shows a maintenance banner or full-page message.
- Super admins bypass the block and can still operate normally.

Maintenance windows are recorded in `opzmain` and cached in Valkey for fast per-request checks (30 s TTL).

---

## DB Schema

**Table:** `maintenance_window` — in `opzmain`

| Column | Type | Nullable | Default | Notes |
|--------|------|---------|---------|-------|
| `id` | uuid | No | gen_random_uuid() | PK |
| `company_id` | uuid | Yes | `null` | `null` = platform-wide; set = single company only |
| `is_active` | boolean | No | `true` | `false` when ended (never deleted — history kept) |
| `reason` | text | Yes | `null` | Human-readable message shown to users, max 500 chars |
| `starts_at` | timestamptz | No | `now()` | When maintenance begins |
| `ends_at` | timestamptz | Yes | `null` | Planned end time; `null` = open-ended (deactivate manually) |
| `created_by` | uuid | No | — | Super admin user ID |
| `created_at` | timestamptz | No | `now()` | Row creation time |

---

## API Endpoints

Base path: `/api/v1/opzhub/maintenance`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/windows` | Super Admin | Create a maintenance window |
| `POST` | `/windows/read` | Super Admin | List windows (paginated) |
| `DELETE` | `/windows/{id}` | Super Admin | Deactivate (end) a window |
| `GET` | `/windows/active` | Any authenticated | Check if currently in maintenance |

### Create Window — Request Body

```json
{
  "company_id": null,
  "reason": "Scheduled database upgrade — back in 30 minutes.",
  "starts_at": "2026-09-11T22:00:00Z",
  "ends_at":   "2026-09-11T22:30:00Z"
}
```

- `company_id: null` → platform-wide maintenance.
- `company_id: "<uuid>"` → single-company maintenance.
- `starts_at` defaults to `now()` if omitted.
- `ends_at` is optional (open-ended window).

### Create Window — Response (201 Created)

```json
{
  "ok": true,
  "data": {
    "id": "f3a1c...",
    "company_id": null,
    "is_active": true,
    "reason": "Scheduled database upgrade — back in 30 minutes.",
    "starts_at": "2026-09-11T22:00:00Z",
    "ends_at":   "2026-09-11T22:30:00Z"
  },
  "correlation_id": "..."
}
```

### Check Active Window — Response

```json
{
  "ok": true,
  "data": {
    "in_maintenance": true,
    "reason": "Scheduled database upgrade — back in 30 minutes.",
    "ends_at": "2026-09-11T22:30:00Z"
  },
  "correlation_id": "..."
}
```

---

## Maintenance Filter (Kernel Integration)

The `maintenance` module registers a `MaintenanceFilter` (Spring `OncePerRequestFilter`) that is evaluated on **every inbound API request**:

```
Inbound request
      │
MaintenanceFilter
      │ 1. Read cache key: maintenance:active:<company_id>
      │    (also check: maintenance:active:global)
      │ 2. If active AND user is NOT super_admin:
      │       → return HTTP 503 with ApiEnvelope error
      │ 3. Otherwise pass through
      ▼
Normal filter chain
```

**Cache key format:**
- Platform-wide: `maintenance:active:global`
- Per-company: `maintenance:active:<company_id>`

**Cache TTL:** 30 seconds. The filter warms the cache from DB on miss.

**HTTP 503 Response Body:**
```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "maintenance",
    "kind": "service_unavailable",
    "msg": "The system is currently under maintenance. Please try again later.",
    "hint": null,
    "fields": null
  },
  "correlation_id": "..."
}
```

Paths that bypass the maintenance filter:
- `GET /healthz` — always available.
- `POST /api/v1/opzhub/identity/resolve` — needed to show maintenance message on login screen.
- `GET /api/v1/opzhub/maintenance/windows/active` — needed for the frontend to know maintenance is active.

---

## SQL Commands

In `modules/maintenance/db/commands/`:

| File | SQL purpose |
|------|------------|
| `maintenance.insert.sql` | INSERT new window, RETURNING id |
| `maintenance.find_active_by_company.sql` | SELECT active window for a specific company_id |
| `maintenance.find_global_active.sql` | SELECT active platform-wide window (company_id IS NULL) |
| `maintenance.list_paged.sql` | SELECT paginated list of all windows |
| `maintenance.deactivate.sql` | UPDATE SET is_active = false WHERE id = :id |

---

## Frontend Integration

The frontend polls or reacts to the maintenance status:

1. On every app load, call `GET /windows/active`.
2. If `in_maintenance: true` → show full-page maintenance message with `reason` and `ends_at`.
3. While in maintenance, the navigation and all forms are blocked for non-admin users.
4. `MaintenanceBannerWidget` can be injected into the `AppShell` header to show a countdown when `ends_at` is known.

---

## Files to Create

```
modules/maintenance/
├── module.yaml                                          ← EXISTS
├── backend/src/main/java/com/managemyopz/modules/maintenance/
│   ├── MaintenanceAutoConfiguration.java                ← NEW
│   ├── api/
│   │   ├── MaintenanceController.java                   ← NEW
│   │   └── dto/
│   │       ├── MaintenanceWindowRequest.java            ← NEW
│   │       └── MaintenanceWindowResponse.java           ← NEW
│   ├── application/
│   │   └── MaintenanceService.java                      ← NEW
│   ├── domain/
│   │   └── MaintenanceWindow.java                       ← NEW (record)
│   └── data/
│       ├── MaintenanceWindowRepository.java             ← NEW (interface)
│       └── DataClientMaintenanceRepository.java         ← NEW (adapter)
├── db/
│   ├── schema/
│   │   └── maintenance_window.yaml                      ← EXISTS
│   └── commands/
│       ├── maintenance.insert.sql                       ← NEW
│       ├── maintenance.find_active_by_company.sql       ← NEW
│       ├── maintenance.find_global_active.sql           ← NEW
│       ├── maintenance.list_paged.sql                   ← NEW
│       └── maintenance.deactivate.sql                   ← NEW
└── frontend/
    ├── index.ts                                         ← NEW
    ├── routes.tsx                                       ← NEW
    └── pages/
        ├── MaintenanceListPage.tsx                      ← NEW
        ├── MaintenanceCreatePage.tsx                    ← NEW
        └── MaintenanceBannerWidget.tsx                  ← NEW (injected into AppShell)
```

Additionally, one **kernel filter** is registered by `MaintenanceAutoConfiguration`:

```
common/backend/src/main/java/com/managemyopz/kernel/web/
└── MaintenanceFilter.java   ← NEW (registered only when maintenance module is present)
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| Deactivating a window sets `is_active = false`; does not DELETE the row | Service calls `maintenance.deactivate.sql` |
| Multiple active windows are allowed (e.g., global + company-specific) | Filter treats ANY active window as in-maintenance |
| Super admin bypasses the filter entirely | Filter checks `SessionAuthentication` for super_admin role |
| Cache miss re-loads from DB | Filter uses `CacheClient.get()` → on null hits `find_active` SQL |
| `reason` max 500 chars | DTO `@Size` constraint |
| `ends_at` must be after `starts_at` if both provided | Application-layer validation in `MaintenanceService` |

---

## Dependencies

- `identity` module (session, RBAC, super_admin role).
- `CacheClient` (Valkey) from the kernel for the 30 s cache.
- `company_information` table in `opzmain` (FK constraint on `company_id`).
- No other modules required.

---

## GUI Metadata Design

### Screen: Create Maintenance Window Form

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `company_id` | Scope | `dropdown` | No | All Companies (null), or pick a specific company | super_admin | Null option = "All Companies" displayed as a special entry at the top |
| `reason` | Reason / Message | `textarea` | No | Max 500 chars; shown to users during maintenance | super_admin | Character counter displayed below field |
| `starts_at` | Starts At | `datetime` | No | ISO 8601; defaults to now() if blank | super_admin | Date-time picker; past values not blocked (to back-date an entry) |
| `ends_at` | Ends At | `datetime` | No | Must be after `starts_at` if both provided | super_admin | Date-time picker; clear button to make open-ended |

### Screen: Maintenance Window List

| Column Key | Heading | Type | Sortable | Role Access |
|-----------|---------|------|---------|-------------|
| `company_id` | Scope | `text` | No | super_admin | "All Companies" when null; company name otherwise |
| `reason` | Reason | `text-truncated` | No | super_admin |
| `is_active` | Active | `status-badge` | Yes | super_admin |
| `starts_at` | Starts At | `datetime` | Yes (default desc) | super_admin |
| `ends_at` | Ends At | `datetime` | No | super_admin | "Open-ended" when null |
| — | Actions | `actions` | No | super_admin | End (deactivate) — visible only when `is_active = true` |

### Widget: Maintenance Banner (shown in AppShell)

| Element Key | Type | Content | Visible When |
|-------------|------|---------|-------------|
| `maintenance_banner` | `info-banner` | Shows `reason` + `ends_at` countdown if set | `in_maintenance: true` AND user is NOT super_admin |
| `maintenance_full_page` | `full-page-overlay` | Full maintenance page with reason text | `in_maintenance: true` AND all API calls return 503 |

### Metadata-Driven Rules

- **End** subaction on the list row is visible **only** when `is_active = true` — `visibleWhen: { is_active: true }` condition in row action metadata.
- `company_id` dropdown loads company list via a lightweight GET (id + name only) — not the full company form data.
- `is_active` badge colors: `true` → warning/amber (maintenance is on), `false` → neutral/grey (historical).
- `reason` textarea is shown to end users during maintenance — must support plain text only (no HTML/markdown injection).

---

## Directory Placement

```
modules/maintenance/                  ← New module folder (non-application-specific)
├── backend/
│   └── …/maintenance/
│       └── MaintenanceAutoConfiguration.java  ← Also registers MaintenanceFilter bean
└── frontend/
    └── pages/
        ├── MaintenanceListPage.tsx
        ├── MaintenanceCreatePage.tsx
        └── MaintenanceBannerWidget.tsx         ← Injected into AppShell, not a page

common/backend/src/main/java/com/managemyopz/kernel/web/
└── MaintenanceFilter.java            ← Kernel filter; registered only when maintenance module present

common/frontend/src/
├── app/
│   └── AppShell.tsx                  ← Imports MaintenanceBannerWidget via module registration
└── fields/display/
    ├── StatusBadge.tsx               ← Reusable (shared with backup-restore)
    └── DatetimeDisplay.tsx           ← Reusable datetime renderer
```

**Rules:**
- `MaintenanceFilter` lives in `common/backend` because it is a **kernel concern** (intercepts every request). It is conditionally registered by `MaintenanceAutoConfiguration` in the module.
- `MaintenanceBannerWidget` is a **module-contributed widget** to the kernel `AppShell`. The AppShell does not import it directly — the module registers it via the module plugin system so the kernel does not depend on a specific module.
- `StatusBadge` and `DatetimeDisplay` are in `common/frontend` — not duplicated in this module.

---

## Constants

### Backend (`modules/maintenance/backend/.../MaintenanceConstants.java`)

```java
public static final String CACHE_KEY_PREFIX_COMPANY  = "maintenance:active:";
public static final String CACHE_KEY_GLOBAL          = "maintenance:active:global";
public static final int    CACHE_TTL_SECONDS         = 30;
public static final int    REASON_MAX_LEN            = 500;
public static final String ERR_CODE_MAINTENANCE      = "maintenance";
public static final String ERR_KIND_MAINTENANCE      = "service_unavailable";
public static final String ERR_MSG_MAINTENANCE       =
    "The system is currently under maintenance. Please try again later.";
// Paths that bypass the maintenance filter
public static final String[] BYPASS_PATH_PREFIXES = {
    "/healthz",
    "/api/v1/opzhub/identity/resolve",
    "/api/v1/opzhub/maintenance/windows/active"
};
```

### Frontend (`modules/maintenance/frontend/maintenanceConstants.ts`)

```typescript
export const MAINTENANCE_API_BASE         = "/api/v1/opzhub/maintenance";
export const MAINTENANCE_ACTIVE_ENDPOINT  = "/api/v1/opzhub/maintenance/windows/active";
export const MAINTENANCE_POLL_INTERVAL_MS = 60_000;   // check every 60 s from frontend
export const MAINTENANCE_PAGE_HEADING     = "Maintenance Mode";
export const MAINTENANCE_REASON_MAX_LEN   = 500;
export const CREATE_WINDOW_LABEL          = "Start Maintenance";
export const END_WINDOW_LABEL             = "End Maintenance";
export const ALL_COMPANIES_LABEL          = "All Companies";
export const OPEN_ENDED_LABEL             = "Open-ended";
```

Active/inactive badge colors are in `common/frontend/src/theme/tokens.ts`:

```typescript
export const MAINTENANCE_ACTIVE_COLOR   = "var(--color-warning)";
export const MAINTENANCE_INACTIVE_COLOR = "var(--color-neutral)";
```

Icons (warning icon, wrench icon) are referenced by icon key from `common/frontend/src/theme/icons.ts` — not hardcoded as SVG paths inside the maintenance module.

---

## Optimization, Performance & Memory

### Performance
- **Cache-first:** `MaintenanceFilter` reads Valkey (`CacheClient.get()`) before hitting the DB. Only on cache miss does it call `find_active` SQL. With 30 s TTL, DB load is bounded to at most one query per 30 s per company regardless of request volume.
- **Index:** `idx_maintenance_window_active(is_active, company_id)` covers both the global and per-company active lookups in a single index scan.
- **Frontend polling:** The banner widget polls `GET /windows/active` every 60 s — not on every page navigation. Polling stops when the user leaves the maintenance screen or logs out.

### Memory
- **Backend:** `MaintenanceFilter` is a singleton Spring bean. It holds no per-request or per-company state in instance fields — all state is in Valkey.
- **Cache:** Active window data cached per company key. When a window is deactivated via `DELETE /windows/{id}`, the service immediately evicts the relevant Valkey key (`CacheClient.delete(CACHE_KEY_PREFIX_COMPANY + companyId)`) so the 30 s TTL does not cause stale maintenance state.
- **React:** The banner polling interval (`setInterval`) is stored in a `useRef` and cleared in `useEffect` cleanup to prevent memory leaks after unmount.
- **Flutter:** If the Flutter app shows a maintenance overlay, use a `Timer` stored as an instance variable and cancel in `dispose()`.

### Optimization
- Bypass paths in `MaintenanceFilter` are checked with `String.startsWith` against the `BYPASS_PATH_PREFIXES` constant array — no regex, O(n × m) but n is tiny (3 entries).
- `maintenance.deactivate.sql` is a targeted single-row `UPDATE SET is_active = false WHERE id = :id` — no full table scan.
- `MaintenanceBannerWidget` renders conditionally only after the first poll result. It does not show a loading state — it is invisible until maintenance is confirmed active.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/modules/maintenance/`.
No test files under `modules/maintenance/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `MaintenanceServiceTest` | Create window — global vs company-scoped; ends_at before starts_at → 400 |
| `MaintenanceFilterTest` | Active window → 503; super_admin bypasses; bypass paths pass through |
| `MaintenanceControllerTest` | `@RequiresPermission` enforced — non-super-admin create → 403 |
| `MaintenanceCacheTest` | Cache hit returns without DB query; eviction on deactivate |

### RBAC in DB

Creating and managing maintenance windows is restricted to `super_admin`.
Reading the active status is available to all authenticated users.

Migration inserts for `id_role_permission`:

```sql
INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions)
VALUES
  ('super_admin', 'maintenance', 'mnt', 'vcd');  -- view + create + deactivate(delete)
```

Controller annotations:

```java
@RequiresPermission(module = "maintenance", feature = "mnt", action = "c")
@PostMapping("/windows")
public ResponseEntity<ApiEnvelope<MaintenanceWindowResponse>> createWindow(...) { ... }

@RequiresPermission(module = "maintenance", feature = "mnt", action = "v")
@PostMapping("/windows/read")
public ResponseEntity<ApiEnvelope<PagedResult<MaintenanceWindowResponse>>> listWindows(...) { ... }

@RequiresPermission(module = "maintenance", feature = "mnt", action = "d")
@DeleteMapping("/windows/{id}")
public ResponseEntity<ApiEnvelope<Void>> deactivateWindow(...) { ... }

// No @RequiresPermission — any authenticated user can check maintenance status
@GetMapping("/windows/active")
public ResponseEntity<ApiEnvelope<MaintenanceStatusResponse>> getActiveStatus(
        SessionAuthentication auth) { ... }
```

`MaintenanceFilter` does **not** use `@RequiresPermission` — it checks the
session's role set directly for `super_admin` to determine bypass:

```java
boolean isSuperAdmin = auth.getRoles().contains("super_admin");
if (isInMaintenance && !isSuperAdmin) {
    // return 503
}
```

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `maintenance.window.create` | Create Maintenance Window |

```sql
-- form_id: maintenance.window.create
-- field_key: company_id    field_type: select    is_mandatory: false  display_order: 1
--   allowed_values: null  (loaded dynamically: GET /companies/read, plus "All Companies" option)
-- field_key: reason        field_type: textarea  is_mandatory: false  display_order: 2
--   max_length: 500  subactions: [{"action":"char_counter","trigger":"change"}]
-- field_key: starts_at     field_type: datetime  is_mandatory: false  display_order: 3
-- field_key: ends_at       field_type: datetime  is_mandatory: false  display_order: 4
--   subactions: [{"action":"validate_after","trigger":"change","targetField":"starts_at"}]
```

`company_id` uses `field_type: select` with `allowed_values: null` — options are
loaded dynamically from `GET /api/v1/opzhub/company-setup/companies/read` and
prepended with an "All Companies" (`null`) option. This follows the `lookup`
pattern from [doc 22](../architecture/22-common-fields-forms-fk.md).

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /windows` | Session | `super_admin` `mnt.c` | None (super_admin can manage all) |
| `POST /windows/read` | Session | `super_admin` `mnt.v` | None |
| `DELETE /windows/{id}` | Session | `super_admin` `mnt.d` | None |
| `GET /windows/active` | Session | None (any authenticated) | Scoped to `company_id` from session |
| `MaintenanceFilter` (all paths) | Session | `super_admin` role check inline | Cache-first; bypass paths skip entirely |

### Coding Standards (this feature)

**Java:**
- `MaintenanceConstants.java` holds cache key prefixes, TTL, reason max length, bypass paths array, and the 503 error message.
- `MaintenanceFilter` is a singleton — no per-request instance fields. State in Valkey only.
- On `DELETE /windows/{id}`: immediately evict `CacheClient.delete(cacheKey)` after the DB update — do not wait for TTL.

**Flutter:**
- `Timer.periodic` for maintenance status polling stored as instance variable, cancelled in `dispose()`.
- `MaintenanceBannerWidget` never rendered unless `in_maintenance: true` — guarded by a conditional render with `false` initial state.
- Constants in `MaintenanceConstants` class. Colors from `common/mobile/lib/theme/tokens.dart`.

**TypeScript/React:**
- `setInterval` for status polling stored in `useRef`; cleared in `useEffect(() => () => clearInterval(ref.current))`.
- Banner color (`var(--color-warning)`) from `tokens.ts` — not hardcoded in `MaintenanceBannerWidget`.
- `BYPASS_PATH_PREFIXES`, `CACHE_TTL_SECONDS`, and error message in `MaintenanceConstants.java` (not duplicated in frontend).

### Directory Confirmation

```
common/backend/.../kernel/web/
    MaintenanceFilter.java          ← kernel filter — registered by module, lives in common
modules/maintenance/
    backend/.../MaintenanceAutoConfiguration.java  ← registers MaintenanceFilter bean
    backend/.../api/MaintenanceController.java
    frontend/pages/MaintenanceBannerWidget.tsx     ← widget injected into AppShell
    mobile/pages/maintenance_banner_widget.dart
```
