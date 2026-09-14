# Requirement: Company Setup

**Module:** `company-setup` (new)
**DB target:** `opzmain`
**Access level:** Super Admin only
**Architecture ref:** [doc 25 §4](../architecture/25-common-features-design.md)

---

## What It Does

Provides admin screens and REST APIs to create and manage the three core registry tables in `opzmain`:

| Entity | Table | Purpose |
|--------|-------|---------|
| Company | `company_information` | The company record with slug, routing mode, status |
| Server Details | `server_details` | DB connection info (IP, port, credentials) for company databases |
| Backend Endpoint | `backend_endpoint` | REST base URL for company-specific backend instances (UseCase 2) |

All operations are restricted to the **super admin** role. No company-level user can access these screens.

---

## Entities & Tables

### `company_information` (opzmain) — v2

| Column | Type | Required | Notes |
|--------|------|---------|-------|
| `id` | uuid | auto | PK, gen_random_uuid() |
| `company_name` | text | Yes | Display name, max 200 chars |
| `company_slug` | text | Yes | URL-safe slug, unique, pattern `^[a-z0-9][a-z0-9-]{1,63}$` |
| `company_references` | jsonb | No | Extra reference strings for email-domain matching |
| `license_code` | text | Yes | FK → `company_license` |
| `server_name` | text | Conditional | FK → `server_details`. Required when `routing_mode = CENTRAL_DB` |
| `routing_mode` | text | Yes | `CENTRAL_DB` \| `COMPANY_BE` \| `SITE`. Default: `CENTRAL_DB` |
| `endpoint_name` | text | Conditional | FK → `backend_endpoint`. Required when `routing_mode = COMPANY_BE` |
| `logo_uri` | text | No | Path or URL for login-screen branding |
| `status` | text | Yes | `active` \| `suspended` \| `expired`. Default: `active` |

### `server_details` (opzmain)

| Column | Type | Required | Notes |
|--------|------|---------|-------|
| `server_name` | text | Yes | PK, natural key (e.g., `acme-db-prod`) |
| `db_ip` | text | Yes | PostgreSQL server IP |
| `db_port` | integer | Yes | PostgreSQL port (typically 5432) |
| `db_username` | text | Yes | DB user |
| `db_password` | text | Yes | DB password — see security note below |

### `backend_endpoint` (opzmain)

| Column | Type | Required | Notes |
|--------|------|---------|-------|
| `endpoint_name` | text | Yes | PK, natural key (e.g., `acme-be-prod`) |
| `be_api_url` | text | Yes | REST base URL of company backend |
| `be_health_url` | text | No | Health check path for `opzhubctl doctor` |
| `be_api_token` | text | No | Optional inter-service bearer token |
| `is_active` | boolean | Yes | Default `true`; set `false` to mark unreachable |

> **Security Note:** `db_password` and `be_api_token` are stored as plain text in this version. Encryption at the application layer (before write) or a secrets-manager reference is a known follow-up.

---

## API Endpoints

Base path: `/api/v1/opzhub/company-setup`

### Companies

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/companies` | Create a company |
| `POST` | `/companies/read` | List companies (paginated) or read by `id` |
| `PUT` | `/companies` | Update a company |
| `DELETE` | `/companies` | Delete a company |

### Server Details

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/server-details` | Create a server entry |
| `POST` | `/server-details/read` | List / read server entries |
| `PUT` | `/server-details` | Update a server entry |
| `DELETE` | `/server-details` | Delete a server entry |

### Backend Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/backend-endpoints` | Create a backend endpoint |
| `POST` | `/backend-endpoints/read` | List / read backend endpoints |
| `PUT` | `/backend-endpoints` | Update a backend endpoint |
| `DELETE` | `/backend-endpoints` | Delete a backend endpoint |

All responses use the standard `ApiEnvelope<T>` wrapper with `correlation_id`.

---

## Company Create Flow (Order Matters)

1. Create the `server_details` entry first (if `routing_mode = CENTRAL_DB`).
2. Create the `backend_endpoint` entry first (if `routing_mode = COMPANY_BE`).
3. Create the `company_information` row referencing the above + a valid `license_code`.

Attempting to create a company that references a non-existent `server_name` or `endpoint_name` results in **400 Bad Request**.

---

## Validation Rules

| Rule | HTTP Status |
|------|------------|
| `company_slug` not unique | 409 Conflict |
| `company_slug` fails pattern `^[a-z0-9][a-z0-9-]{1,63}$` | 400 Bad Request |
| `routing_mode = CENTRAL_DB` and `server_name` is null | 400 Bad Request |
| `routing_mode = COMPANY_BE` and `endpoint_name` is null | 400 Bad Request |
| `license_code` does not exist | 400 Bad Request |
| Deleting `server_details` still referenced by a company | 409 Conflict (FK restrict) |
| Deleting `backend_endpoint` still referenced by a company | 409 Conflict (FK restrict) |

---

## Files to Create

```
modules/company-setup/
├── module.yaml                                    ← EXISTS
├── backend/src/main/java/com/managemyopz/modules/companysetup/
│   ├── CompanySetupAutoConfiguration.java         ← NEW
│   ├── api/
│   │   ├── CompanyController.java                 ← NEW
│   │   ├── ServerDetailsController.java           ← NEW
│   │   ├── BackendEndpointController.java         ← NEW
│   │   └── dto/
│   │       ├── CompanyCreateRequest.java          ← NEW
│   │       ├── CompanyUpdateRequest.java          ← NEW
│   │       ├── CompanyResponse.java               ← NEW
│   │       ├── ServerDetailsRequest.java          ← NEW
│   │       ├── ServerDetailsResponse.java         ← NEW
│   │       ├── BackendEndpointRequest.java        ← NEW
│   │       └── BackendEndpointResponse.java       ← NEW
│   ├── application/
│   │   ├── CompanyService.java                    ← NEW
│   │   └── ServerDetailsService.java              ← NEW
│   ├── domain/
│   │   ├── Company.java                           ← NEW
│   │   ├── ServerDetails.java                     ← NEW
│   │   └── BackendEndpoint.java                   ← NEW
│   └── data/
│       ├── CompanyRepository.java                 ← NEW (interface)
│       ├── DataClientCompanyRepository.java       ← NEW (adapter)
│       ├── ServerDetailsRepository.java           ← NEW (interface)
│       └── DataClientServerDetailsRepository.java ← NEW (adapter)
├── db/commands/
│   ├── company.insert.sql                         ← NEW
│   ├── company.find_by_id.sql                     ← NEW
│   ├── company.find_by_slug.sql                   ← NEW
│   ├── company.list_paged.sql                     ← NEW
│   ├── company.count_total.sql                    ← NEW
│   ├── company.update.sql                         ← NEW
│   ├── company.delete.sql                         ← NEW
│   ├── server_details.insert.sql                  ← NEW
│   ├── server_details.find_by_name.sql            ← NEW
│   ├── server_details.list_all.sql                ← NEW
│   ├── server_details.update.sql                  ← NEW
│   ├── server_details.delete.sql                  ← NEW
│   ├── backend_endpoint.insert.sql                ← NEW
│   ├── backend_endpoint.find_by_name.sql          ← NEW
│   ├── backend_endpoint.list_all.sql              ← NEW
│   ├── backend_endpoint.update.sql                ← NEW
│   └── backend_endpoint.delete.sql               ← NEW
├── frontend/
│   ├── index.ts                                   ← NEW
│   ├── routes.tsx                                 ← NEW
│   └── pages/
│       ├── CompanyListPage.tsx                    ← NEW
│       ├── CompanyCreatePage.tsx                  ← NEW
│       ├── CompanyEditPage.tsx                    ← NEW
│       ├── ServerDetailsListPage.tsx              ← NEW
│       ├── ServerDetailsFormPage.tsx              ← NEW
│       ├── BackendEndpointListPage.tsx            ← NEW
│       └── BackendEndpointFormPage.tsx            ← NEW
└── mobile/
    ├── plugin.dart                                ← NEW
    └── pages/
        └── company_list_page.dart                 ← NEW
```

---

## Dependencies

- `modules/licensing/db/schema/company_information.yaml` must be version 2 (with routing fields).
- `modules/licensing/db/schema/backend_endpoint.yaml` must exist.
- `modules/licensing/db/schema/server_details.yaml` must exist.
- `company_license` table must already be populated (license codes created separately).
- Requires `identity` module for session / RBAC (`super_admin` role check).

---

## GUI Metadata Design

Every form and list screen renders from a field-definition metadata object. Labels, types, validation rules, allowed values, and subactions are defined in metadata — never hardcoded in JSX or Dart widgets.

### Screen: Company Form (Create / Edit)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `company_name` | Company Name | `text` | Yes | Max 200 chars | super_admin | — |
| `company_slug` | Company Slug | `text` | Yes | `^[a-z0-9][a-z0-9-]{1,63}$`; auto-suggest from name | super_admin | Live uniqueness check on blur (GET `/companies?slug=`) |
| `company_references` | Alias / Email Domains | `tags` | No | Comma-separated; each entry lowercase | super_admin | Add / remove chips inline |
| `license_code` | License Code | `lookup` | Yes | Values from `company_license` table | super_admin | Lookup search; show expiry date as hint |
| `routing_mode` | Routing Mode | `dropdown` | Yes | `CENTRAL_DB`, `COMPANY_BE`, `SITE` | super_admin | Changing mode toggles visibility of `server_name` / `endpoint_name` fields |
| `server_name` | Server Entry | `lookup` | Conditional | Values from `server_details.server_name`; required if `routing_mode = CENTRAL_DB` | super_admin | Visible only when `routing_mode` is `CENTRAL_DB` or `SITE` |
| `endpoint_name` | Backend Endpoint | `lookup` | Conditional | Values from `backend_endpoint.endpoint_name`; required if `routing_mode = COMPANY_BE` | super_admin | Visible only when `routing_mode = COMPANY_BE` |
| `logo_uri` | Logo URL / Path | `text` | No | URL or `/brand/<slug>.svg` path | super_admin | Preview image on change |
| `status` | Status | `dropdown` | Yes | `active`, `suspended`, `expired` | super_admin | Changing to `suspended` shows confirmation dialog |

### Screen: Server Details Form

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `server_name` | Server Name | `text` | Yes | Max 64 chars, `^[a-z0-9][a-z0-9-_]{1,63}$` | super_admin | Immutable after create (PK) |
| `db_ip` | Database Host / IP | `text` | Yes | IPv4, IPv6, or hostname | super_admin | — |
| `db_port` | Database Port | `number` | Yes | 1–65535; default 5432 | super_admin | — |
| `db_username` | DB Username | `text` | Yes | Max 64 chars | super_admin | — |
| `db_password` | DB Password | `password` | Yes | — | super_admin | Reveal/hide toggle; masked in display mode |

### Screen: Backend Endpoint Form

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `endpoint_name` | Endpoint Name | `text` | Yes | Max 64 chars | super_admin | Immutable after create (PK) |
| `be_api_url` | Backend API URL | `url` | Yes | Must start with `https://` | super_admin | Test connectivity button (calls `/healthz`) |
| `be_health_url` | Health Check Path | `text` | No | Relative path, e.g. `/healthz` | super_admin | — |
| `be_api_token` | API Token | `password` | No | — | super_admin | Reveal/hide toggle |
| `is_active` | Active | `toggle` | Yes | true / false | super_admin | Deactivating shows confirmation if companies reference it |

### Screen: Company List

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `company_name` | Company | `text` | Yes | super_admin |
| `company_slug` | Slug | `text` | Yes | super_admin |
| `routing_mode` | Routing | `badge` | Yes | super_admin |
| `status` | Status | `status-badge` | Yes | super_admin |
| `license_code` | License | `text` | No | super_admin |
| — | Actions | `actions` | No | super_admin | Edit, Suspend, Delete |

### Metadata-Driven Rules

- `routing_mode` change **must** conditionally show/hide `server_name` and `endpoint_name` — this toggle is driven by field metadata `visibleWhen` conditions, not hardcoded `if` blocks in the component.
- `db_password` and `be_api_token` must render as masked fields; the "reveal" subaction is part of field metadata (`subactions: ["reveal"]`).
- Slug field triggers a uniqueness check subaction on blur — this is a `subactions: ["check_unique"]` metadata entry linked to `GET /companies/read?slug=<value>`.

---

## Directory Placement

```
modules/company-setup/           ← Module folder (non-application-specific admin function)
├── backend/                     ← Java Spring Boot module
├── frontend/                    ← React module pages
└── mobile/                      ← Flutter module pages

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reusable FK lookup (used by server_name, endpoint_name, license_code)
│   ├── TagsField/               ← Reusable multi-chip input (used by company_references)
│   └── StatusBadge/             ← Reusable status badge display
└── kernel/constants/
    └── commonConstants.ts       ← Shared field type keys, badge color mappings

common/mobile/lib/
└── fields/
    ├── lookup_field.dart
    └── tags_field.dart
```

**Rules:**
- The `LookupField`, `TagsField`, and `StatusBadge` components are in `common/frontend` — reused by company-setup and any other module that needs them.
- Company-setup pages import from `common/` for field primitives and from `modules/company-setup/frontend/` for domain-specific logic.
- No business logic (API calls, validation) inside `common/` components — only rendering.

---

## Constants

### Backend (`modules/company-setup/backend/.../CompanySetupConstants.java`)

```java
public static final String ROUTING_MODE_CENTRAL_DB = "CENTRAL_DB";
public static final String ROUTING_MODE_COMPANY_BE = "COMPANY_BE";
public static final String ROUTING_MODE_SITE       = "SITE";
public static final String STATUS_ACTIVE           = "active";
public static final String STATUS_SUSPENDED        = "suspended";
public static final String STATUS_EXPIRED          = "expired";
public static final int    COMPANY_SLUG_MAX_LEN    = 64;
public static final int    SERVER_NAME_MAX_LEN     = 64;
public static final String SLUG_PATTERN            = "^[a-z0-9][a-z0-9-]{1,63}$";
```

### Frontend (`modules/company-setup/frontend/companySetupConstants.ts`)

```typescript
export const COMPANY_API_BASE       = "/api/v1/opzhub/company-setup";
export const ROUTING_MODE_OPTIONS   = [
  { value: "CENTRAL_DB", label: "Central DB"       },
  { value: "COMPANY_BE", label: "Company Backend"  },
  { value: "SITE",       label: "On-Site"           },
];
export const STATUS_OPTIONS         = [
  { value: "active",    label: "Active"    },
  { value: "suspended", label: "Suspended" },
  { value: "expired",   label: "Expired"   },
];
export const SLUG_FIELD_LABEL       = "Company Slug";
export const SLUG_PATTERN           = /^[a-z0-9][a-z0-9-]{1,63}$/;
export const DB_PORT_DEFAULT        = 5432;
export const HTTPS_URL_PREFIX       = "https://";
```

Colors and icons for status badges are in `common/frontend/src/theme/tokens.ts` — not in this module.

---

## Optimization, Performance & Memory

### Performance
- Company list uses server-side pagination via `company.list_paged.sql` — never fetch all rows.
- Lookup fields (server_name, endpoint_name, license_code) load options lazily on field focus, not on page load.
- Slug uniqueness check is debounced (300 ms) and cancelled on each new keystroke.
- Server-Details and Backend-Endpoint lists use `list_all.sql` since these registries are typically small (< 100 rows). If they grow, add pagination later.

### Memory
- **React:** Form state (all field values) lives in `useReducer` — not scattered in multiple `useState` calls. Dispose on unmount.
- **Lookup field:** Fetched options are cached in component-local `useRef` for the session duration — not in global Redux/Zustand store to avoid unbounded growth.
- **Flutter:** `TextEditingController` instances are disposed in the `State.dispose()` method — one controller per field, not shared.

### Optimization
- `db_password` and `be_api_token` values are **never** sent in list responses — only in the single-record GET and create/update flows.
- Backend RETURNING clause on INSERT avoids a second SELECT round-trip.
- `idx_company_information_company_slug` unique index guarantees O(log n) slug lookup during resolve.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/modules/company-setup/`.
No test files under `modules/company-setup/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `CompanyServiceTest` | Create/update validation, slug uniqueness, routing_mode FK rules |
| `CompanyControllerTest` | RBAC annotation enforcement — non-admin gets 403 |
| `ServerDetailsServiceTest` | Insert, update, delete with FK reference check |
| `BackendEndpointServiceTest` | URL validation, is_active toggle, health-check path |

### RBAC in DB

All company-setup endpoints require `super_admin` role. The system role
`super_admin` is seeded in `id_role` with `is_system = true` by the `identity`
module migration. `id_role_permission` rows are inserted by the
`company-setup` module migration.

Migration inserts for `id_role_permission`:

```sql
INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions)
VALUES
  ('super_admin', 'company-setup', 'co',  'vcud'),
  ('super_admin', 'company-setup', 'srv', 'vcud'),
  ('super_admin', 'company-setup', 'bep', 'vcud');
```

Every controller method carries `@RequiresPermission`:

```java
@RequiresPermission(module = "company-setup", feature = "co", action = "c")
@PostMapping("/companies")
public ResponseEntity<ApiEnvelope<CompanyResponse>> createCompany(...) { ... }

@RequiresPermission(module = "company-setup", feature = "co", action = "v")
@PostMapping("/companies/read")
public ResponseEntity<ApiEnvelope<PagedResult<CompanyResponse>>> listCompanies(...) { ... }

@RequiresPermission(module = "company-setup", feature = "co", action = "u")
@PutMapping("/companies")
public ResponseEntity<ApiEnvelope<CompanyResponse>> updateCompany(...) { ... }

@RequiresPermission(module = "company-setup", feature = "co", action = "d")
@DeleteMapping("/companies")
public ResponseEntity<ApiEnvelope<Void>> deleteCompany(...) { ... }
```

Same pattern for `srv` and `bep` features.

### Form Metadata in DB

Every create/edit form is registered in `id_field_definition`. Migration
inserts rows for all fields with their `is_mandatory`, `field_type`,
`allowed_values`, `format_pattern`, and `role_visibility`.

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `company-setup.company.create` | Create Company |
| `company-setup.company.edit` | Edit Company |
| `company-setup.server-details.create` | Create Server Entry |
| `company-setup.server-details.edit` | Edit Server Entry |
| `company-setup.backend-endpoint.create` | Create Backend Endpoint |
| `company-setup.backend-endpoint.edit` | Edit Backend Endpoint |

Example migration row (company slug field):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, max_length, format_pattern, display_order,
  subactions, role_visibility
) VALUES (
  'company-setup.company.create', 'company_slug', 'Company Slug', 'text',
  true, 64, '^[a-z0-9][a-z0-9-]{1,63}$', 2,
  '[{"action":"check_unique","trigger":"blur"}]'::jsonb,
  '{"default":"edit"}'::jsonb
);
```

`routing_mode` field uses `allowed_values` JSONB so the dropdown options
come from DB — no hardcoded array in JSX or Dart:

```sql
-- field_key: routing_mode, field_type: select,
-- allowed_values: [{"value":"CENTRAL_DB","label":"Central DB"},
--                  {"value":"COMPANY_BE","label":"Company Backend"},
--                  {"value":"SITE","label":"On-Site"}]
```

### API-Level RBAC/ABAC

- All 12 CRUD endpoints across the three entities use `@RequiresPermission`.
- No ABAC policy on company-setup itself (super_admin can manage all companies).
- `db_password` and `be_api_token` are **excluded** from list responses by the
  repository projection — the SQL selects everything except credential columns
  in list queries.

### Coding Standards (this feature)

**Java:**
- Constants: `CompanySetupConstants.java` — routing mode strings, status values, slug pattern.
- All SQL in `modules/company-setup/db/commands/`.
- No logic in `CompanyController` — delegates to `CompanyService`.
- `CompanyCreateRequest.toString()` masks `db_password` and `be_api_token`.

**Flutter:**
- `DropdownField` and `LookupField` widgets from `common/mobile/lib/fields/`.
- `CompanySetupConstants` class in `modules/company-setup/mobile/lib/constants/`.
- All `TextEditingController` instances disposed in `dispose()`.
- Never display `db_password` or `be_api_token` in plain text in the UI.

**TypeScript/React:**
- `ROUTING_MODE_OPTIONS`, `STATUS_OPTIONS`, and `COMPANY_API_BASE` in `companySetupConstants.ts`.
- `LookupField` and `TagsField` imported from `common/frontend` — not re-implemented.
- `db_password` and `be_api_token` fields use the `PasswordField` primitive from `common/frontend`.

### Directory Confirmation

```
common/frontend/src/fields/controls/
    LookupField/      ← FK lookup — used by server_name, endpoint_name, license_code
    TagsField/        ← multi-chip input — used by company_references
modules/company-setup/
    backend/          ← all controller + service + repository Java
    frontend/         ← pages using common field primitives
    mobile/           ← Flutter pages using common widgets
    db/commands/      ← all SQL named commands
```
