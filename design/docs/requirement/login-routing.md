# Requirement: Login Auto Routing

**Module:** `identity` (extension)
**DB target:** `opzmain` (read-only during resolve)
**Access level:** Public (pre-authentication)
**Architecture ref:** [doc 25 §3](../architecture/25-common-features-design.md), [doc 20](../architecture/20-licensing-site-central.md)

---

## What It Does

When a user opens the login screen and types a company identifier (e.g., `acme/ada` or `ada@acme.com`), the frontend calls the **resolve endpoint** before showing the password field. The resolve endpoint:

1. Parses the company slug from the typed value.
2. Looks up the company in `opzmain` (table: `company_information`).
3. Returns the correct **API origin** and **GUI origin** the frontend should use for login and all subsequent calls.

This enables two deployment topologies without any frontend code branching:

| `routing_mode` | What resolve returns |
|----------------|---------------------|
| `CENTRAL_DB` | Hub URL for both `api` and `gui`. Login and data stay on the central BE; the `DataRouter` switches to the company's DB transparently. |
| `COMPANY_BE` | Company-specific BE URL as `api`, hub URL as `gui`. After resolve the browser redirects all API calls to the company's own backend. |
| `SITE` | Company appliance URL for both `api` and `gui`. |

---

## Resolve Endpoint

**URL:** `POST /api/v1/opzhub/identity/resolve`
**Auth:** Not required.

### Request

```json
{ "q": "acme/ada", "live": true }
```

| Field | Type | Required | Notes |
|-------|------|---------|-------|
| `q` | string | Yes | Raw login field value typed by the user |
| `live` | boolean | No | `true` = debounced field call; may use short cache TTL |

### Response (success)

```json
{
  "ok": true,
  "co": "acme",
  "u":  "ada",
  "n":  "Acme Ltd",
  "logo": "/brand/acme.svg",
  "gui": "https://hub.example.com",
  "api": "https://hub.example.com",
  "st": "ok",
  "skin": "lite"
}
```

| Field | Meaning |
|-------|---------|
| `co` | Resolved company slug |
| `u` | Resolved username part |
| `n` | Company display name |
| `logo` | Company logo URI (for login screen branding) |
| `gui` | FE origin — browser stays here |
| `api` | BE origin — all API calls (including `/login`) go here |
| `st` | `ok` \| `wait` \| `bad` (never exposes expired vs unknown) |
| `skin` | UI chrome hint: `lite` \| `rich` |

### Response (failure / unknown company)

```json
{ "ok": false, "st": "bad" }
```

Generic error — never enumerate companies or distinguish expired from unknown.

---

## Preflight Endpoint

**URL:** `POST /api/v1/opzhub/identity/preflight`
**Auth:** Not required.

Called when the user focuses the password field to confirm the login method is still allowed before the password is typed.

```json
{ "co": "acme", "method": "password" }
```

Response: `{ "ok": true }` or generic `{ "ok": false }`.

---

## Supported Login Identifier Formats

| Typed | Parsed as |
|-------|-----------|
| `acme/ada` | company `acme`, user `ada` |
| `ada@acme.com` | company matched by `company_references` containing `acme.com`, user `ada` |

- Company slug rule: `^[a-z0-9][a-z0-9-]{1,63}$`
- Bare usernames (no company prefix) → rejected.

---

## DB Query (opzmain)

Two SQL commands are added to `modules/identity/db/commands/`:

| File | Purpose |
|------|---------|
| `identity.resolve_company_by_slug.sql` | SELECT from `company_information` JOIN `backend_endpoint` WHERE `company_slug = :slug` AND `status = 'active'` |
| `identity.resolve_company_by_reference.sql` | SELECT from `company_information` WHERE `company_references @> :ref::jsonb` AND `status = 'active'` |

Fields read: `id`, `company_name`, `company_slug`, `routing_mode`, `logo_uri`, `status`, `be_api_url` (from `backend_endpoint` join).

---

## Files to Create / Modify

```
modules/identity/
├── backend/src/main/java/com/managemyopz/modules/identity/
│   ├── api/routing/
│   │   ├── CompanyResolveController.java      ← NEW
│   │   └── dto/
│   │       ├── ResolveRequest.java            ← NEW
│   │       └── ResolveResponse.java           ← NEW
│   ├── application/routing/
│   │   ├── CompanyRoutingService.java         ← NEW
│   │   └── RoutingMode.java                   ← NEW (enum: CENTRAL_DB, COMPANY_BE, SITE)
│   └── data/routing/
│       └── CompanyInformationRepository.java  ← NEW
└── db/commands/
    ├── identity.resolve_company_by_slug.sql   ← NEW
    └── identity.resolve_company_by_reference.sql ← NEW
```

**module.yaml** — add `/resolve` and `/preflight` to `provides.routes`.

---

## Business Rules

1. Rate-limit resolve per IP + slug prefix (prevent enumeration).
2. Cache live-resolve results for `license.validate_ttl_seconds` (45 s default). Password submit must bypass cache and do a fresh lookup.
3. When `routing_mode = COMPANY_BE` and `backend_endpoint.is_active = false` → return `st: bad`.
4. Never include `db_host`, `db_password`, or the full `backend_endpoint` row in the resolve response.
5. Resolve must complete within the debounce window (400 ms). Keep the query lightweight — one JOIN, indexed lookup.

---

## Dependencies

- `company_information` table must have `company_slug`, `routing_mode`, `endpoint_name`, `logo_uri`, `status` columns (v2 schema).
- `backend_endpoint` table must exist for `COMPANY_BE` lookups.
- No authentication dependency — this runs before login.

---

## GUI Metadata Design

All form fields and display elements must be driven by metadata. The frontend renders fields from a field-definition object — never hardcodes labels, types, or validation in the component itself.

### Screen: Login Page (pre-resolve)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `login_identifier` | Login ID | `text` | Yes | `company/username` or `user@domain.com` | All | Debounce 400 ms → call `/resolve`; show company branding panel on slug ≥ 2 chars |
| `password` | Password | `password` | Yes | Min 8 chars | All | Reveal/hide toggle; shown only after resolve returns `st: ok` and `auth` contains `password` |
| `remember_me` | Remember Me | `checkbox` | No | true / false | All | Extends session TTL on server |

### Display Panel: Company Branding (shown after resolve `st: ok`)

| Element Key | Heading | Type | Source | Notes |
|-------------|---------|------|--------|-------|
| `company_logo` | — | `image` | `resolve.logo` | Fallback to platform default logo if null |
| `company_name` | — | `text` | `resolve.n` | Shown above the password field |
| `skin` | — | `theme` | `resolve.skin` | Applied immediately to login screen chrome |

### Metadata-Driven Rules

- Password field **must not render** until resolve returns `st: ok`.
- If resolve returns `st: bad`, show a generic inline error — never expose "expired" vs "unknown".
- `login_identifier` field placeholder text and label text come from `common/frontend` constants — not hardcoded in the login component.
- Role-based: no role required (pre-auth); the form itself has no RBAC gate.

---

## Directory Placement

```
common/
└── frontend/src/
    ├── auth/
    │   └── resolveApi.ts          ← Resolve + preflight API calls (reusable by all FE modules)
    ├── fields/controls/
    │   └── LoginIdentifierField/  ← Reusable company/username field with debounce + branding hook
    └── kernel/
        └── constants/
            └── identityConstants.ts  ← LOGIN_DEBOUNCE_MS, RESOLVE_MIN_SLUG_LEN

modules/identity/
├── backend/…/api/routing/
│   └── CompanyResolveController.java   ← Module-level, not common
├── frontend/
│   └── pages/LoginPage.tsx             ← Uses LoginIdentifierField from common
└── mobile/
    └── pages/login_page.dart
```

- **Rule:** The debounced resolve hook lives in `common/frontend` because it may be used by any solution's login page.
- **Rule:** The company branding panel is a `common/frontend` component — not duplicated per module.
- **Rule:** Backend routing logic stays in `modules/identity`, not in the kernel.

---

## Constants

### Backend (`modules/identity/backend/.../IdentityApplicationConstants.java`)

```java
public static final String RESOLVE_CACHE_KEY_PREFIX = "resolve:company:";
public static final int    RESOLVE_CACHE_TTL_SECONDS = 45;
public static final String ROUTING_MODE_CENTRAL_DB   = "CENTRAL_DB";
public static final String ROUTING_MODE_COMPANY_BE   = "COMPANY_BE";
public static final String ROUTING_MODE_SITE         = "SITE";
public static final String COMPANY_STATUS_ACTIVE      = "active";
public static final String RESOLVE_ST_OK             = "ok";
public static final String RESOLVE_ST_BAD            = "bad";
```

### Frontend (`common/frontend/src/kernel/constants/identityConstants.ts`)

```typescript
export const LOGIN_DEBOUNCE_MS       = 400;
export const RESOLVE_MIN_SLUG_LEN    = 2;
export const RESOLVE_ENDPOINT        = "/api/v1/opzhub/identity/resolve";
export const PREFLIGHT_ENDPOINT      = "/api/v1/opzhub/identity/preflight";
export const LOGIN_ENDPOINT          = "/api/v1/opzhub/identity/login";
export const LOGIN_ID_LABEL          = "Login ID";
export const LOGIN_ID_PLACEHOLDER    = "company/username or email@domain.com";
export const PASSWORD_LABEL          = "Password";
export const REMEMBER_ME_LABEL       = "Remember me";
export const RESOLVE_GENERIC_ERROR   = "Company not found or not active.";
```

### Mobile (`modules/identity/mobile/lib/constants/identity_constants.dart`)

```dart
const int    kLoginDebounceMs    = 400;
const int    kResolveMinSlugLen  = 2;
const String kResolveEndpoint    = '/api/v1/opzhub/identity/resolve';
const String kLoginIdLabel       = 'Login ID';
const String kPasswordLabel      = 'Password';
```

Icons and colors live in `common/frontend/src/theme/` and `common/mobile/lib/theme/` — not inside the identity module.

---

## Optimization, Performance & Memory

### Performance
- **Debounce:** Resolve fires at most once per 400 ms. Always cancel the previous in-flight request before sending a new one (`AbortController` in React; `CancelToken`/`Dio` cancellation in Flutter).
- **Cache (backend):** Resolve results are cached in Valkey with a 45 s TTL. The password-submit path **bypasses cache** and always performs a live DB lookup.
- **DB query:** `identity.resolve_company_by_slug.sql` uses the `idx_company_information_company_slug` unique index — single-row lookup, no full table scan.
- **No N+1:** The resolve query JOINs `backend_endpoint` in one statement — it does not make a second query for the endpoint URL.

### Memory
- **React:** The `AbortController` ref must be cleaned up in the `useEffect` teardown to prevent state updates on unmounted components (`isPageMountedRef` pattern already in `LoginPage.tsx`).
- **Flutter:** Use `Dio` cancel tokens stored as instance variables; cancel on `dispose()`.
- **Backend cache:** Resolve entries are short-lived (45 s TTL). Do not cache the raw `server_details` row — only the resolve response shape.

### Optimization
- The login page must not import any module-specific bundles. Only `common/frontend` code is loaded before authentication.
- Company logo is fetched lazily after resolve returns — not preloaded.
- `skin` theme switch on resolve must not cause a full-page re-render; apply only to the login panel CSS class.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests live in the **separate repo** `managemyopz-testing/01-unit/modules/identity/`.
No test files under `modules/identity/backend/src/test/`.

New test classes to create:

| Class | What it tests |
|-------|--------------|
| `CompanyResolveControllerTest` | Resolve endpoint — ok / bad / COMPANY_BE routing mode |
| `CompanyRoutingServiceTest` | Slug parse, email-domain match, cache hit/miss |
| `ResolveRateLimitTest` | Rate-limit rejects after threshold; passes before |

### RBAC in DB

The resolve endpoint is **pre-auth** — no RBAC check required on `/resolve` or
`/preflight`. After login, the session stores the matrix computed from
`id_role_permission` + `id_user_permission` (doc 26 §3).

The `@RequiresPermission` annotation is **not** applied to `/resolve` or
`/preflight`. It IS applied to all admin endpoints (e.g., company list read):

```java
// No annotation on resolve — it is public
@PostMapping("/resolve")
public ResponseEntity<ApiEnvelope<ResolveResponse>> resolve(...) { ... }

// Identity admin endpoints require role permission
@RequiresPermission(module = "identity", feature = "usr", action = "v")
@PostMapping("/users/read")
public ResponseEntity<ApiEnvelope<PagedResult<UserResponse>>> listUsers(...) { ... }
```

### Form Metadata in DB

The login form fields (`login_identifier`, `password`, `remember_me`) are
registered in `id_field_definition` under `form_id = "identity.login"`.
The frontend calls `GET /forms/identity.login?mode=create` to get field
definitions before rendering. This allows the mandatory flag, placeholder,
and field order to be changed without a frontend deployment.

```sql
-- form_id: "identity.login"
-- field_key: "login_identifier", is_mandatory: true, field_type: "text"
-- field_key: "password",         is_mandatory: true, field_type: "password"
-- field_key: "remember_me",      is_mandatory: false, field_type: "boolean"
```

### API-Level RBAC/ABAC

All endpoints that return company or routing data enforce at minimum:
- `/resolve` — no RBAC (pre-auth, public)
- `/preflight` — no RBAC (pre-auth, public)
- `/login` — no RBAC (authenticate-to-get-RBAC)
- `/session` — authenticated session required (401 if not logged in)
- `/logout` — authenticated session required

Admin-only identity endpoints (`/users`, `/roles`) carry
`@RequiresPermission(module="identity", feature="usr"|"rol", action=...)`.

### Coding Standards (this feature)

**Java:**
- `CompanyRoutingService` returns `ApiEnvelope<ResolveResponse>` — no raw maps.
- Resolve response DTO overrides `toString()` to mask `be_api_token` if included.
- All SQL in `modules/identity/db/commands/identity.resolve_*.sql`.
- Constants in `IdentityApplicationConstants.java`.

**Flutter:**
- `LoginPage` disposes `TextEditingController` for the identifier field in `dispose()`.
- `resolveApi.ts` is a `common/frontend` import — not duplicated in `mobile/`.
- The Dio cancel token for in-flight resolve requests is cancelled in widget `dispose()`.

**TypeScript/React:**
- `AbortController` for in-flight resolve fetch is created per request, stored in `useRef`, cancelled in `useEffect` cleanup.
- `LOGIN_DEBOUNCE_MS`, `RESOLVE_ENDPOINT`, and all labels in `identityConstants.ts` — no inline strings.

### Directory Confirmation

```
common/frontend/src/auth/resolveApi.ts          ← shared resolve + preflight API calls
common/frontend/src/fields/controls/
    LoginIdentifierField/                       ← debounce + branding hook (common)
modules/identity/backend/.../api/routing/
    CompanyResolveController.java               ← endpoint implementation (module)
modules/identity/frontend/pages/LoginPage.tsx   ← uses common LoginIdentifierField
```
