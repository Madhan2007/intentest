# 26 — RBAC / ABAC Database Design & API-Level Enforcement

Extends [doc 18](18-identity-rbac-abac-oauth2.md). This document covers the
normalized DB table design for roles and permissions, user-specific overrides,
the session-matrix build algorithm, form-field metadata storage, and the
annotation/AOP pattern used to enforce RBAC on every API endpoint.

---

## 1. Why Normalized Tables Instead of JSONB Roles Column

`id_user.roles JSONB` was an initial placeholder. Problems with it:

| Problem | Impact |
|---------|--------|
| No FK integrity — roles are free text | Stale/typo role names never detected |
| Grant editing requires full JSON column replace | Race condition risk on concurrent edits |
| No user-specific permission overrides | Every override requires a custom role |
| Query `users WHERE role = 'admin'` needs `@>` JSONB operator | Index on JSONB is GIN — larger and slower than a join |
| Role audit trail is impossible | Cannot answer "who granted this?" |

The normalized design below replaces the JSONB column with five tables.

---

## 2. RBAC DB Schema (opzuser — per-company)

All five tables live in the company's `opzuser` database. They are created
by `modules/identity` migrations (`module.yaml: migrations.backend: true`).

### `id_role` — Role catalogue

```yaml
entity: id_role
table: id_role
version: 1
database: OPZUSER
columns:
  - name: role_code
    type: text
    primary_key: true
    nullable: false
    max_length: 64
    comment: >
      Short machine key. Pattern: ^[a-z0-9][a-z0-9._-]{1,63}$
      e.g. "admin", "hr.manager", "tkt.agent"
  - name: role_title
    type: text
    nullable: false
    max_length: 200
    comment: Display name shown in the role admin UI.
  - name: is_system
    type: boolean
    nullable: false
    default: "false"
    comment: >
      true = built-in role shipped with the module; cannot be deleted.
      e.g. "super_admin", "company_admin".
  - name: is_active
    type: boolean
    nullable: false
    default: "true"
  - name: created_at
    type: timestamptz
    nullable: false
    default: now()
indexes:
  - name: idx_id_role_active
    columns: [is_active]
```

### `id_user_role` — User ↔ Role assignment

```yaml
entity: id_user_role
table: id_user_role
version: 1
database: OPZUSER
columns:
  - name: user_id
    type: uuid
    primary_key: true
    nullable: false
    comment: FK → id_user.id
  - name: role_code
    type: text
    primary_key: true
    nullable: false
    max_length: 64
    comment: FK → id_role.role_code
  - name: assigned_at
    type: timestamptz
    nullable: false
    default: now()
  - name: assigned_by
    type: uuid
    nullable: true
    comment: User ID of the admin who made the assignment. Null = system bootstrap.
indexes:
  - name: idx_id_user_role_user_id
    columns: [user_id]
  - name: idx_id_user_role_role_code
    columns: [role_code]
foreign_keys:
  - column: user_id
    references_table: id_user
    references_column: id
    on_delete: cascade
    enforce: postgres
  - column: role_code
    references_table: id_role
    references_column: role_code
    on_delete: cascade
    enforce: postgres
```

### `id_role_permission` — Role → module/feature/actions grant

```yaml
entity: id_role_permission
table: id_role_permission
version: 1
database: OPZUSER
columns:
  - name: role_code
    type: text
    primary_key: true
    nullable: false
    max_length: 64
    comment: FK → id_role.role_code
  - name: module_id
    type: text
    primary_key: true
    nullable: false
    max_length: 32
    comment: Module id as declared in module.yaml, e.g. "hr", "tkt", "*"
  - name: feature_id
    type: text
    primary_key: true
    nullable: false
    max_length: 16
    comment: Feature id from module.yaml access.features, e.g. "emp", "*"
  - name: permissions
    type: text
    nullable: false
    max_length: 5
    comment: >
      Packed letter string, e.g. "vcua".
      Letters: v=view c=create u=update d=delete a=approve
indexes:
  - name: idx_id_role_permission_role_code
    columns: [role_code]
foreign_keys:
  - column: role_code
    references_table: id_role
    references_column: role_code
    on_delete: cascade
    enforce: postgres
```

### `id_user_permission` — User-specific permission overrides

Rows here **override** the role-derived matrix for a specific user. Used for
granting extra access or explicitly revoking access regardless of role.

```yaml
entity: id_user_permission
table: id_user_permission
version: 1
database: OPZUSER
columns:
  - name: user_id
    type: uuid
    primary_key: true
    nullable: false
    comment: FK → id_user.id
  - name: module_id
    type: text
    primary_key: true
    nullable: false
    max_length: 32
  - name: feature_id
    type: text
    primary_key: true
    nullable: false
    max_length: 16
  - name: permissions
    type: text
    nullable: false
    max_length: 5
    comment: The effective permission letters when grant_type = GRANT.
  - name: grant_type
    type: text
    nullable: false
    max_length: 6
    default: "'GRANT'"
    comment: >
      GRANT — add the listed permissions on top of role grants.
      REVOKE — remove the listed permissions even if the role allows them.
  - name: created_at
    type: timestamptz
    nullable: false
    default: now()
indexes:
  - name: idx_id_user_permission_user_id
    columns: [user_id]
foreign_keys:
  - column: user_id
    references_table: id_user
    references_column: id
    on_delete: cascade
    enforce: postgres
```

### `id_field_definition` — Form field metadata (mandatory, type, allowed values)

Answers the question: **"Is format/mandatory in DB?"** — **Yes.** This table is
the canonical source. The `FormEnvelope` API reads it; DTO annotations enforce
the same rules at the API boundary. Both layers must stay in sync.

```yaml
entity: id_field_definition
table: id_field_definition
version: 1
database: OPZUSER
columns:
  - name: form_id
    type: text
    primary_key: true
    nullable: false
    max_length: 128
    comment: >
      Opaque form identifier registered by a module.
      Pattern: <module>.<entity>.<mode>
      e.g. "company-setup.company.create", "hr.emp.edit"
  - name: field_key
    type: text
    primary_key: true
    nullable: false
    max_length: 64
    comment: Machine key matching the DTO field name and DB column name.
  - name: field_heading
    type: text
    nullable: false
    max_length: 200
    comment: Display label shown to the user. Translated via i18n key at render time.
  - name: field_type
    type: text
    nullable: false
    max_length: 32
    comment: >
      Renderer key from the common field registry (doc 22 §2).
      Allowed: text | textarea | number | boolean | select | multiselect |
               radio | lookup | lookup-multi | password | datetime | tags
  - name: is_mandatory
    type: boolean
    nullable: false
    default: "false"
    comment: >
      When true, the field is required. Enforced:
        1. DB — NOT NULL or CHECK constraint where applicable.
        2. DTO — @NotNull / @NotBlank annotation.
        3. FormEnvelope — req: true returned to frontend.
        4. Frontend — client-side validation before submit.
  - name: max_length
    type: integer
    nullable: true
    comment: Maximum allowed length for text fields.
  - name: min_length
    type: integer
    nullable: true
  - name: format_pattern
    type: text
    nullable: true
    comment: >
      Regex pattern for server-side and client-side validation.
      e.g. "^[a-z0-9][a-z0-9-]{1,63}$" for company_slug.
  - name: allowed_values
    type: jsonb
    nullable: true
    comment: >
      Static option list for select/radio/multiselect fields.
      Format: [{"value": "lite", "label": "Light"}, ...]
      Null means options are loaded dynamically via a lookup endpoint.
  - name: default_value
    type: text
    nullable: true
    comment: String representation of the default. Parsed by field_type.
  - name: display_order
    type: integer
    nullable: false
    default: "0"
    comment: Ascending order within the form.
  - name: field_group
    type: text
    nullable: true
    max_length: 64
    comment: >
      Logical section/group label for visual grouping in the form.
      e.g. "Database Connection", "Routing"
  - name: role_visibility
    type: jsonb
    nullable: true
    comment: >
      Per-role rendering mode override.
      Format: {"admin": "edit", "viewer": "readonly", "default": "edit"}
      Values: edit | readonly | hidden
  - name: subactions
    type: jsonb
    nullable: true
    comment: >
      List of subactions attached to this field.
      Format: [{"action": "check_unique", "label": null, "trigger": "blur",
                "visibleWhen": null}]
  - name: is_active
    type: boolean
    nullable: false
    default: "true"
indexes:
  - name: idx_id_field_definition_form_id
    columns: [form_id]
```

**Validation stack (four layers — all must agree):**

```
id_field_definition.is_mandatory / format_pattern
        ↓ served by
FormEnvelope API → frontend renders req indicator + validates before submit
        ↓ also enforced by
Java DTO @NotNull / @NotBlank / @Pattern / @Size
        ↓ final guard
DB NOT NULL / CHECK constraint (schema)
```

---

## 3. Matrix Build Algorithm (login + session)

At login, `MatrixBuilderService` computes the access matrix and stores it in
the session cache. This replaces reading `id_user.roles` JSONB.

```
Step 1 — Load roles
  SELECT role_code FROM id_user_role WHERE user_id = :uid AND is_active role

Step 2 — Load role permissions (single query with IN clause)
  SELECT module_id, feature_id, permissions
    FROM id_role_permission
   WHERE role_code = ANY(:role_codes)

Step 3 — Union role permissions per (module_id, feature_id)
  For each pair: merge letter strings (union of distinct letters, max 5)
  e.g. role A grants "vc", role B grants "va" → merged "vca"

Step 4 — Load user-specific overrides
  SELECT module_id, feature_id, permissions, grant_type
    FROM id_user_permission
   WHERE user_id = :uid

Step 5 — Apply overrides
  GRANT rows: add letters to merged set
  REVOKE rows: remove letters from merged set

Step 6 — Build compact matrix (doc 18 §4 format)
  Omit entries with empty permission string after revokes
  {"hr": {"*": "vcua", "emp": "vcu"}, "tkt": {"inc": "v"}}

Step 7 — Store in CacheClient
  Key: session:{session_id}:matrix  TTL: session_ttl_seconds
```

**Matrix invalidation:** When roles or user-specific permissions are changed,
call `SessionCacheService.invalidateMatrix(userId)` — this evicts all active
sessions for that user forcing re-login. Configurable via
`security.matrix_invalidate_on_role_change: true` (default: true).

---

## 4. API-Level RBAC Enforcement (every endpoint)

Doc 18 §1 states: **"Every API call re-evaluates RBAC+ABAC."**

### 4.1 `@RequiresPermission` Annotation

Defined in `common/backend`:

```java
// common/backend/.../kernel/security/RequiresPermission.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    /** Module id from module.yaml, e.g. "company-setup" */
    String module();
    /** Feature id from module.yaml access.features, e.g. "co". Default "*" */
    String feature() default "*";
    /** Single action letter: v | c | u | d | a */
    String action();
}
```

AOP aspect in `common/backend`:

```java
// common/backend/.../kernel/security/PermissionCheckAspect.java
@Aspect
@Component
public class PermissionCheckAspect {

    @Before("@annotation(rp)")
    public void checkPermission(JoinPoint jp, RequiresPermission rp) {
        SessionAuthentication auth = SecurityContextHolder
            .getContext().getAuthentication() instanceof SessionAuthentication sa
            ? sa : null;
        if (auth == null) throw new UnauthenticatedException();
        if (!auth.getMatrix().has(rp.module(), rp.feature(), rp.action())) {
            throw new AccessDeniedException(
                "Permission denied: " + rp.module() + "." + rp.feature()
                + "." + rp.action());
        }
    }
}
```

**Usage in every controller method:**

```java
// modules/company-setup/.../api/CompanyController.java
@RequiresPermission(module = "company-setup", feature = "co", action = "c")
@PostMapping("/companies")
public ResponseEntity<ApiEnvelope<CompanyResponse>> createCompany(
        @Valid @RequestBody CompanyCreateRequest request,
        Authentication auth) {
    // no permission logic here — annotation + AOP handled it
    return ok(companyService.create(request));
}

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

### 4.2 ABAC Resource Check (service layer)

For resource-scoped actions (e.g., "can this user edit THIS employee?"),
the service calls `AccessService` after the RBAC check passes:

```java
// In the service, NOT the controller
public CompanyResponse update(CompanyUpdateRequest request, SessionAuthentication auth) {
    // RBAC already checked by @RequiresPermission on the controller
    // ABAC: check if user can modify this specific company (multi-company admins)
    accessService.requireAbac("company-setup", "co", "u",
        Map.of("company_id", request.getId()),
        auth);
    // ... proceed with update
}
```

`AccessService.requireAbac()` evaluates the ABAC policy rows from
`id_abac_policy` (doc 18 §3.2) and throws `AccessDeniedException` (HTTP 403)
if denied. Logs the denial with correlation ID — never the reason in the
response body.

### 4.3 Permission Matrix Reference Table (all common-feature modules)

| Module | Feature | v | c | u | d | a |
|--------|---------|---|---|---|---|---|
| `company-setup` | `co` | List/read companies | Create | Edit | Delete | — |
| `company-setup` | `srv` | List server details | Create | Edit | Delete | — |
| `company-setup` | `bep` | List backend endpoints | Create | Edit | Delete | — |
| `user-settings` | `prof` | Read own profile | — | Update own | — | — |
| `backup-restore` | `bkp` | List jobs | Trigger backup | — | — | — |
| `backup-restore` | `rst` | — | Trigger restore | — | — | Approve restore |
| `maintenance` | `mnt` | Read windows | Create window | — | Deactivate | — |
| `identity` | `usr` | List users | Create user | Edit user | Delete user | — |
| `identity` | `rol` | List roles | Create role | Edit role | Delete role | Assign role |

> `v` on `user-settings.prof` is restricted to the user's own row by ABAC
> (`user_id == session.user_id`). The RBAC check passes for all authenticated
> users; ABAC limits the row scope.

---

## 5. Unit Test Location

**Rule:** Unit tests are in the **separate** `managemyopz-testing` repository,
not in `modules/*/backend/src/test/`.

```
managemyopz-testing/
└── 01-unit/
    ├── modules/
    │   ├── identity/
    │   │   ├── AuthServiceTest.java               ← migrated from identity src/test
    │   │   └── SchemaCompanyDirectoryTest.java    ← migrated
    │   ├── apps/
    │   │   └── CompanyApplicationServiceTest.java ← migrated
    │   ├── crud/
    │   │   ├── GenericCrudServiceTest.java        ← migrated
    │   │   └── GenericCrudControllerTest.java     ← migrated
    │   ├── company-setup/
    │   ├── user-settings/
    │   ├── backup-restore/
    │   └── maintenance/
    └── common/
        └── kernel/
            ├── MatrixBuilderServiceTest.java      ← new (RBAC matrix build)
            ├── PermissionCheckAspectTest.java     ← new (annotation enforcement)
            └── MaintenanceFilterTest.java         ← new
```

**Files to migrate from main repo:**

| Current path | Target in managemyopz-testing |
|---|---|
| `modules/identity/backend/src/test/.../AuthServiceTest.java` | `01-unit/modules/identity/AuthServiceTest.java` |
| `modules/identity/backend/src/test/.../SchemaCompanyDirectoryTest.java` | `01-unit/modules/identity/SchemaCompanyDirectoryTest.java` |
| `modules/apps/backend/src/test/.../CompanyApplicationServiceTest.java` | `01-unit/modules/apps/CompanyApplicationServiceTest.java` |
| `modules/crud/backend/src/test/.../GenericCrudServiceTest.java` | `01-unit/modules/crud/GenericCrudServiceTest.java` |
| `modules/crud/backend/src/test/.../GenericCrudControllerTest.java` | `01-unit/modules/crud/GenericCrudControllerTest.java` |

After migration, the `src/test/` folders in the main repo hold only integration
test stubs (if any) — not unit test logic.

---

## 6. Coding Standards (all implementation files)

### Java (backend)

- Organization/Owner/Created-at Javadoc block on every new class.
- `@RequiresPermission` on every `@PostMapping` / `@PutMapping` / `@DeleteMapping`.
- Constants in `<Module>Constants.java` — one per module, no inline strings.
- DB access through `DataClient` only. No raw JDBC.
- All public service methods return `ApiEnvelope<T>` or throw typed exceptions.
- Run Checkstyle + SpotBugs on changed files.

### Flutter (mobile)

- File header: `// Organization: Technosprint info Solutions | Owner: Logaraj S | Created: <date>`
- Hardcoded non-display values in feature `<Feature>Constants` class — one per package.
- `TextEditingController`, streams, and subscriptions disposed in `dispose()`.
- Passwords never encrypted/hashed in Flutter; cleared from controllers after submit.
- DartDoc on all public classes and methods (`/// description`).
- DB access never from Flutter — only through the backend API.
- Run `flutter analyze` on changed files; fix all warnings before commit.

### Python (scripts)

- Header block: Organization / Owner / Created at / Description.
- Hardcoded values in `constants.py` — one per folder.
- DB I/O only through `database_manager.py`.
- Type hints on every method; `StandardResponse` return on public methods.
- Run `pylint` on changed files; fix all findings.

### Frontend (TypeScript/React)

- Feature constants in `<feature>Constants.ts` — one per module.
- Colors, spacing, icon keys in `common/frontend/src/theme/tokens.ts` only.
- No business logic in page components — delegate to API hooks and services.
- `AbortController` / polling intervals cleaned up in `useEffect` teardown.
