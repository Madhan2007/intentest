# Requirement: Change Password

**Module:** `identity` (extension)
**DB target:** `opzuser` (per-company)
**Access level:** Any authenticated user (changes their own password only)
**Architecture ref:** [doc 25 §6](../architecture/25-common-features-design.md)

---

## What It Does

Allows a logged-in user to change their own account password. The feature:

1. Verifies the user's **current password** against the stored Argon2id hash.
2. Validates the **new password** meets strength requirements and differs from the current one.
3. Verifies `new_password == confirm_password`.
4. Updates the `password_hash` column in `id_user` (in the company's `opzuser` database).

The user does not need to log out and back in after changing their password. The existing session token remains valid.

---

## API Endpoint

**URL:** `PUT /api/v1/opzhub/identity/change-password`
**Auth:** Required (`opzhub_session` cookie or `Authorization: Bearer <token>`).

### Request

```json
{
  "current_password": "OldSecret!1",
  "new_password":     "NewSecret@2",
  "confirm_password": "NewSecret@2"
}
```

| Field | Type | Required | Validation |
|-------|------|---------|-----------|
| `current_password` | string | Yes | Must match stored Argon2id hash |
| `new_password` | string | Yes | Min 8 chars; must differ from `current_password` |
| `confirm_password` | string | Yes | Must equal `new_password` |

### Response (success)

```json
{ "ok": true, "data": null, "error": null, "correlation_id": "..." }
```
HTTP `200 OK`.

### Response (wrong current password)

```json
{
  "ok": false,
  "error": {
    "code": "invalid_credentials",
    "kind": "unauthenticated",
    "msg": "Current password is incorrect.",
    "hint": null,
    "fields": null
  },
  "correlation_id": "..."
}
```
HTTP `401 Unauthorized`.

### Response (validation failure)

HTTP `400 Bad Request` with `error.fields` map showing which field failed.

---

## DB Changes

No new table. One new SQL command in `modules/identity/db/commands/`:

**`identity.update_password_hash.sql`**
```sql
UPDATE id_user
   SET password_hash = :hash
 WHERE id = :user_id;
```

- Uses `NamedParameterJdbcTemplate` via `DataClient` (no raw JDBC).
- Runs inside a `DataClient.transaction(Isolation.READ_COMMITTED, ...)` block.
- The `user_id` comes from `SessionAuthentication.getUserId()` — never from the request body.

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| User cannot see the result of `verify(wrong_password, hash)` timing | Dummy hash check is already in `AuthService`; same pattern applies here |
| New password must differ from current | Application layer check before DB update |
| Only the session owner can change their own password | `user_id` sourced from validated session, not request body |
| Argon2id is the only accepted hashing algorithm | `PasswordEncoder` bean (Spring Security) — same as login |
| Failed current-password verification: no retry limit per request | Brute-force protection is handled at the gateway/rate-limiter level, not here |

---

## Files to Create / Modify

```
modules/identity/
├── backend/src/main/java/com/managemyopz/modules/identity/
│   ├── api/
│   │   ├── ChangePasswordController.java          ← NEW
│   │   └── dto/
│   │       └── ChangePasswordRequest.java         ← NEW
│   └── application/
│       └── ChangePasswordService.java             ← NEW
└── db/commands/
    └── identity.update_password_hash.sql          ← NEW
```

**module.yaml** — add `/change-password` to `provides.routes`.

---

## Controller Skeleton

```java
@PutMapping("/change-password")
public ResponseEntity<ApiEnvelope<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication,
        HttpServletRequest httpRequest) {
    // extract user_id from SessionAuthentication only
    // delegate to ChangePasswordService
    // return 200 ok or 401 / 400
}
```

No SQL logic, no direct DataClient access — those belong in `ChangePasswordService` and the repository.

---

## Dependencies

- `identity` module must be present (session, `id_user` table).
- `PasswordEncoder` bean (Argon2id) from `IdentityAutoConfiguration`.
- `DataClient` from the kernel for the DB update.
- No additional tables or modules required.

---

## GUI Metadata Design

### Screen: Change Password Form

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `current_password` | Current Password | `password` | Yes | — | Any authenticated user | Reveal/hide toggle |
| `new_password` | New Password | `password` | Yes | Min 8 chars; must differ from current | Any authenticated user | Reveal/hide toggle; strength indicator (weak / fair / strong) |
| `confirm_password` | Confirm New Password | `password` | Yes | Must match `new_password` | Any authenticated user | Reveal/hide toggle; live match check on change |

### Metadata-Driven Rules

- **Strength indicator** on `new_password` is a `subactions: ["strength_meter"]` entry — rendered by the `PasswordField` component in `common/frontend`, not by the change-password page.
- **Live match check** on `confirm_password` fires on every keystroke and compares against `new_password` in local state — no API call needed.
- All field labels and validation messages come from constants — never hardcoded in JSX or Dart.
- The submit button is disabled until both passwords match and `current_password` is non-empty.

---

## Directory Placement

```
modules/identity/                         ← Existing module; extend, do not create a new module
├── backend/…/identity/
│   ├── api/
│   │   ├── ChangePasswordController.java ← New file in the existing api/ folder
│   │   └── dto/
│   │       └── ChangePasswordRequest.java
│   └── application/
│       └── ChangePasswordService.java
├── frontend/
│   └── pages/
│       └── ChangePasswordPage.tsx        ← New page in the existing pages/ folder
└── mobile/
    └── pages/
        └── change_password_page.dart

common/frontend/src/fields/controls/
└── PasswordField/                        ← Reusable password field with reveal toggle + strength
    ├── PasswordField.tsx                 ← Already exists or extend if present
    └── StrengthMeter.tsx                 ← Sub-component; in common, not in identity module
```

**Rules:**
- `ChangePasswordPage` uses the `PasswordField` primitive from `common/frontend` — it does not re-implement reveal/hide or the strength meter.
- The strength meter logic and the reveal toggle belong in `common/frontend/src/fields/controls/PasswordField/` so all modules that use password input benefit.
- The route `/change-password` is registered by the `identity` module — not by a separate module.

---

## Constants

### Backend (`modules/identity/backend/.../IdentityApplicationConstants.java` — extend)

```java
public static final int    PASSWORD_MIN_LENGTH           = 8;
public static final String ERR_CURRENT_PASSWORD_WRONG   = "Current password is incorrect.";
public static final String ERR_NEW_PASSWORD_SAME        = "New password must differ from the current password.";
public static final String ERR_PASSWORDS_DO_NOT_MATCH   = "Passwords do not match.";
```

### Frontend (`modules/identity/frontend/identityFrontendConstants.ts` — extend)

```typescript
export const CHANGE_PASSWORD_HEADING          = "Change Password";
export const CURRENT_PASSWORD_LABEL           = "Current Password";
export const NEW_PASSWORD_LABEL               = "New Password";
export const CONFIRM_PASSWORD_LABEL           = "Confirm New Password";
export const PASSWORD_MIN_LENGTH              = 8;
export const CHANGE_PASSWORD_SUBMIT_LABEL     = "Update Password";
export const CHANGE_PASSWORD_SUBMITTING_LABEL = "Updating…";
export const CHANGE_PASSWORD_SUCCESS_MSG      = "Password updated successfully.";
export const CHANGE_PASSWORD_ENDPOINT         = "/api/v1/opzhub/identity/change-password";
```

### Mobile (`modules/identity/mobile/lib/constants/identity_constants.dart` — extend)

```dart
const int    kPasswordMinLength          = 8;
const String kChangePasswordHeading      = 'Change Password';
const String kCurrentPasswordLabel       = 'Current Password';
const String kNewPasswordLabel           = 'New Password';
const String kConfirmPasswordLabel       = 'Confirm New Password';
const String kChangePasswordEndpoint     = '/api/v1/opzhub/identity/change-password';
```

Strength-meter color tokens (`weak → red`, `fair → amber`, `strong → green`) live in `common/frontend/src/theme/tokens.ts` — not in the identity constants.

---

## Optimization, Performance & Memory

### Performance
- Argon2id verification and hashing are CPU-intensive. The `ChangePasswordService` must **not** block the virtual thread pool. Spring Boot 3 with virtual threads handles this — no explicit `@Async` annotation needed, but do not call the service from a reactive pipeline.
- There is no preliminary `SELECT` before the `UPDATE` — existence of the user is guaranteed by the session. The single `UPDATE id_user SET password_hash = :hash WHERE id = :user_id` checks affected rows; zero rows → session integrity error (log and return 500, not 401).

### Memory
- **React:** Password field values must be cleared from component state immediately on successful submission or on unmount. Do not store passwords in URL params, local storage, or global state.
- **Flutter:** `TextEditingController.dispose()` must be called in `State.dispose()` for all three password controllers. After submission, call `.clear()` before navigating away.
- **Backend:** The plaintext password string must not be logged at any log level. The `ChangePasswordRequest` DTO must override `toString()` to mask password fields.

### Optimization
- The strength meter computation runs **client-side only** — no API call per keystroke.
- Live confirm-match check is a simple string comparison in local state — zero network cost.
- Submit disables immediately on click to prevent double-submission.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/modules/identity/`.
No test files under `modules/identity/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `ChangePasswordServiceTest` | Wrong current password → 401; same password → 400; success → 200 |
| `ChangePasswordControllerTest` | `user_id` sourced from session; body user_id ignored |
| `PasswordPolicyTest` | Min length, blank, confirm mismatch |

### RBAC in DB

Change password is available to **any authenticated user** on their own account.
No role permission required beyond being logged in — `SessionAuthFilter` is the gate.

`@RequiresPermission` is **not** applied to this endpoint. ABAC equivalent is
enforcing `user_id` from `SessionAuthentication`, not the request body:

```java
@PutMapping("/change-password")
public ResponseEntity<ApiEnvelope<Void>> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        SessionAuthentication auth) {
    changePasswordService.changePassword(request, auth.getUserId());
    return ok(null);
}
```

`id_user_permission` REVOKE rows cannot block a user's own password change.

### Form Metadata in DB

Form `id = "identity.change-password"` registered in `id_field_definition`:

```sql
-- field_key: current_password   field_type: password  is_mandatory: true  display_order: 1
-- field_key: new_password        field_type: password  is_mandatory: true  display_order: 2
--   min_length: 8  subactions: [{"action":"strength_meter","trigger":"change"}]
-- field_key: confirm_password    field_type: password  is_mandatory: true  display_order: 3
--   subactions: [{"action":"match_check","trigger":"change","targetField":"new_password"}]
```

`strength_meter` and `match_check` subactions are in `id_field_definition` —
not in a hardcoded `if (formId === ...)` condition in the `PasswordField` widget.

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `PUT /change-password` | `SessionAuthFilter` (401 if missing) | None (self-service) | `user_id` locked to session — body cannot override |

Audit log: `user_id`, `correlation_id`, timestamp. Never log password or hash bytes.

### Coding Standards (this feature)

**Java:** `ChangePasswordRequest.toString()` returns `"[REDACTED]"` for all password fields. Error constants in `IdentityApplicationConstants.java`.

**Flutter:** After submit success, call `.clear()` on all three controllers before navigation. Dispose all controllers in `dispose()`.

**TypeScript/React:** Password state cleared in `useEffect` cleanup and on success. Never stored in global state or `localStorage`.

### Directory Confirmation

```
modules/identity/ (extend existing module)
    backend/.../api/ChangePasswordController.java
    frontend/pages/ChangePasswordPage.tsx
    mobile/pages/change_password_page.dart
common/frontend/src/fields/controls/PasswordField/StrengthMeter.tsx  ← common
```
