# Implementation Rules (all features)

These rules apply to **every** feature implemented in ManageMyOpz.
Each requirement document references this file. Feature-specific applications
of each rule are in the individual requirement files.

**Architecture refs:**
- RBAC/ABAC DB design: [doc 26](../architecture/26-rbac-db-design.md)
- Form field metadata: [doc 22](../architecture/22-common-fields-forms-fk.md)
- Identity/RBAC overview: [doc 18](../architecture/18-identity-rbac-abac-oauth2.md)
- Repository structure: [doc 02](../architecture/02-repository-folder-structure.md)
- Per-application database & cross-app data access: [doc 28](../architecture/28-per-application-database-design.md)

---

## Rule 1 — Unit Test Cases (separate repo)

| Item | Rule |
|------|------|
| Test repository | `managemyopz-testing` (separate Git repo from the main product) |
| Unit tests subfolder | `managemyopz-testing/01-unit/` |
| Mirror path | `01-unit/modules/<module-name>/` mirrors the main repo module layout |
| Integration tests | `managemyopz-testing/02-integration/` (separate folder, not in scope yet) |
| Main repo `src/test/` | Must not contain unit test logic — only empty placeholder or integration stubs |
| Test file naming | `<ClassUnderTest>Test.java` for Java; `<class_under_test>_test.dart` for Flutter; `test_<module>.py` for Python |

**Existing tests to migrate:**

| File | Current location | Target in managemyopz-testing |
|------|---------|------|
| `AuthServiceTest.java` | `modules/identity/backend/src/test/…` | `01-unit/modules/identity/` |
| `SchemaCompanyDirectoryTest.java` | `modules/identity/backend/src/test/…` | `01-unit/modules/identity/` |
| `CompanyApplicationServiceTest.java` | `modules/apps/backend/src/test/…` | `01-unit/modules/apps/` |
| `GenericCrudServiceTest.java` | `modules/crud/backend/src/test/…` | `01-unit/modules/crud/` |
| `GenericCrudControllerTest.java` | `modules/crud/backend/src/test/…` | `01-unit/modules/crud/` |

---

## Rule 2 — RBAC in DB with Optimized Tables

RBAC grants are stored in normalized tables in `opzuser`, not in a JSONB
column. See [doc 26 §2](../architecture/26-rbac-db-design.md) for full schema.

| Table | Purpose |
|-------|---------|
| `id_role` | Role catalogue (code, title, system flag) |
| `id_user_role` | User ↔ role assignments with audit trail |
| `id_role_permission` | Role → module/feature/action grant |
| `id_user_permission` | User-specific permission GRANT or REVOKE overrides |

**Matrix build:** At login, `MatrixBuilderService` reads these four tables,
computes the compact matrix (doc 18 §4 format), and stores it in the session
cache. Changing roles or user-specific permissions invalidates all active
sessions for that user.

**User-specific RBAC:** Use `id_user_permission` to grant or revoke
individual permissions without creating a custom role. `grant_type = GRANT`
adds letters; `grant_type = REVOKE` removes them from the role-derived set.

---

## Rule 3 — Format / Mandatory in DB (confirmed)

**Yes — format and mandatory rules are stored in `id_field_definition`.**

The validation chain has four layers that must always agree:

```
id_field_definition (DB)
    └── served by FormEnvelope API (GET /api/v1/opzhub/forms/{form_id})
            └── frontend renders required indicator + validates before submit
Java DTO annotations (@NotNull, @NotBlank, @Pattern, @Size)
    └── enforced at API layer on every request body
DB NOT NULL / CHECK constraints
    └── last-resort guard; never the only layer
```

Each module that owns forms must:
1. Insert `id_field_definition` rows in its migration script.
2. Declare the same constraints in its DTO annotations.
3. Register the `form_id` in `module.yaml` under `provides.forms`.

The frontend **always** calls `GET /forms/{form_id}?mode=create|edit|view`
before rendering a form. It never hardcodes `required: true` or field
options in JSX / Dart widgets.

---

## Rule 4 — RBAC/ABAC at API / Backend Level

Every protected API endpoint **must** have explicit RBAC enforcement. UI
matrix is for show/hide only — it is not the security boundary.

### Java (backend)

Use `@RequiresPermission` annotation on every controller method:

```java
// From common/backend/kernel/security
@RequiresPermission(module = "feature-module", feature = "feat", action = "c")
@PostMapping("/resource")
public ResponseEntity<ApiEnvelope<Dto>> createResource(...) { ... }
```

For resource-scoped ABAC checks, call in the service (not the controller):

```java
accessService.requireAbac("module", "feat", "u",
    Map.of("owner_id", resource.getOwnerId()), auth);
```

HTTP responses:
- Not authenticated → `401 Unauthorized`
- RBAC denied → `403 Forbidden` (generic message, never the policy reason)
- ABAC denied → `403 Forbidden` (same)

### Flutter (mobile)

- Do not perform RBAC checks in Flutter widgets. The matrix is used **only**
  for hide/show/disable — not as a security gate.
- Check `matrix.has(module, feature, action)` before showing buttons;
  the server will always enforce independently.
- If the server returns 403, show a generic "Access denied" error using
  the common error handler — do not try to parse the reason.

### Python (scripts)

- Scripts invoked by `opzhubctl` run server-side under an internal service
  account — they bypass the HTTP RBAC layer.
- Authorization for script operations (backup, restore) is enforced at the
  API layer before dispatching the script (the Java service checks
  `@RequiresPermission` before calling the script command).

---

## Rule 5 — Directory Structure (common / modules / apps)

| Folder | Contents | Rule |
|--------|----------|------|
| `common/backend` | Kernel filters, `DataClient`, `CacheClient`, `RequiresPermission`, `PermissionCheckAspect`, `MaintenanceFilter`, `ApiEnvelope`, `StandardResponse` | No module-specific logic |
| `common/frontend` | Primitive field controls, `StatusBadge`, `DatetimeDisplay`, theme tokens, icon registry, `AppShell`, `resolveApi.ts`, locale constants | No domain/business imports |
| `common/mobile` | Common Flutter widgets, API client, secure storage, exception classes, theme | No domain/business imports |
| `common/scripts` | `run-job.sh`, `opzhubctl` CLI wrapper, flock helpers | No module-specific logic |
| `modules/<name>` | Non-application-specific platform features (identity, crud, admin, company-setup, user-settings, backup-restore, maintenance, licensing) | Cross-module imports forbidden |
| `apps/<name>` | Application-specific features (hr, ticketing, inventory, …) | Must not be imported by modules |
| `solutions/<name>` | Customer-specific delivery configurations | No new business logic |

**Reusability checklist before creating a new file:**
1. Does the primitive exist in `common/`? Use it.
2. Is this domain logic? Put it in `modules/<name>/application/` or `apps/<name>/`.
3. Is this a shared DB query pattern? Add a named SQL command in `db/commands/`.
4. Is this a UI primitive (field, badge, formatter)? Put it in `common/frontend`.
5. Never duplicate constants between modules — if two modules need the same
   constant, it belongs in `common/`.

---

## Rule 6 — Hardcoded Values, Colors, Icons, CSS

| Category | Location |
|----------|---------|
| API endpoint paths | `<module>Constants.ts` / `<Module>Constants.java` |
| Field labels and headings | `<module>Constants.ts` (frontend) / `<module>Constants.dart` (mobile) |
| Status / enum string values | `<Module>Constants.java` (backend) and frontend constants |
| Theme colors, font scale | `common/frontend/src/theme/tokens.ts` |
| Icon keys | `common/frontend/src/theme/icons.ts` |
| CSS custom properties | `common/frontend/src/theme/global.css` |
| Polling intervals, debounce times | Module-specific constants file |
| Cache TTL values | Backend constants file for the owning module |
| Error messages (user-visible) | Backend constants file; never inline in catch blocks |

**Never:**
- Hardcode colors as `#hex` or `rgb()` inside component files.
- Hardcode icon SVG paths inside module pages.
- Hardcode status strings (`"active"`, `"PENDING"`) in multiple places — one constant, referenced everywhere.

---

## Rule 7 — Performance and Memory (universal)

| Topic | Rule |
|-------|------|
| Pagination | All list endpoints are paginated server-side. Never `SELECT *` without LIMIT. |
| Caching | Use `CacheClient` (Valkey). Set explicit TTL on every key. Evict on mutation. |
| N+1 queries | Join at SQL level — never loop + query. |
| Frontend polling | Store interval ID in `useRef`; clear in `useEffect` cleanup. |
| Flutter timers | Cancel `Timer` in `dispose()`. Cancel `StreamSubscription` in `dispose()`. |
| React state | Use `useReducer` for multi-field forms. Don't scatter into many `useState`. |
| Flutter controllers | One `TextEditingController` per field. Call `.dispose()` in `State.dispose()`. |
| Password fields | Clear controllers after submit. Never store in global state or local storage. |
| Backend threads | CPU-heavy work (Argon2id, pg_dump dispatch) must not block request threads. Use virtual threads (Spring Boot 3 default). |
| DB connections | All DB access through `DataClient` connection pool. Never open raw connections. |

---

## Rule 8 — Per-Application Database

Every sold application under `apps/<name>` gets its **own** logical
Postgres database, not a shared `opzmain`. Full rule and rationale:
[doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).

| Item | Rule |
|------|------|
| Naming | `OPZ<SHORT>` — app_key minus `manage-my-` prefix, upper-cased. `manage-my-people` → `OPZPEOPLE`. |
| Examples | `OPZPEOPLE`, `OPZDESK`, `OPZDATA`, `OPZMARKET`, `OPZFINANCE` |
| Declared | Per-entity `database:` field in `apps/<name>/db/schema/*.yaml` — no new manifest key needed |
| Platform databases (`OPZMAIN`, `OPZUSER`, `OPZHUB`) | Reserved for core registry / identity / app-catalog data. A sold app never adds its own tables to these. |
| Never | A new sold app's business tables declared under `database: OPZMAIN` |

---

## Rule 9 — Cross-Application Data Access (no duplication)

When app A needs data app B owns, query B's own database through B's own
named commands — never copy the column into A. Full rule:
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication).

| Item | Rule |
|------|------|
| Reference | Store the foreign id only (logical FK, same shape as [doc 22](../architecture/22-common-fields-forms-fk.md) §6.2) |
| Postgres `FOREIGN KEY` across databases | Never — not physically possible |
| Read | Second `DataClient` query against the owning app's database, via its registered named command |
| Write | Application-layer existence check before insert/update, same `Conflict`/`err: fk` shape as same-database FKs |
| Batching | Resolve id lists with one `find_by_ids`-style call — never one cross-database query per row |
| Caching | `CacheClient`, explicit TTL, invalidated by the **owning** app's write path |
| Never | Duplicating another app's column locally "to make the join easier"; an un-invalidated local cache used as a substitute for a live read; assuming the two apps' writes are one transaction |
