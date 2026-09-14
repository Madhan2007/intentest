# Requirement: Manage My HR

**App:** `manage-my-hr` (new, `apps/manage-my-hr`) — a **presentation-only wrapper** over `apps/manage-my-people`
**DB target:** none — this app adds **zero new tables** (see What It Does)
**Access level:** Reuses `apps/manage-my-people`'s roles/permissions entirely — no new roles
**Architecture ref:** [doc 33](../architecture/33-hr-wrapper-design.md) · [doc 27](../architecture/27-people-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md)
**Reference source:** none new — see [doc 33 §1](../architecture/33-hr-wrapper-design.md#1-what-this-app-is-right-now)
for why this app has no schema of its own this drop.

---

## What It Does

An "HR" branded menu, dashboard, and set of routes over
`apps/manage-my-people`, defaulting and locking `category = 'employee'`
so an HR user sees a people directory, attendance view, and leave
console scoped to employees — without the generic cross-category admin
surface `apps/manage-my-people` itself exposes (org-unit/role-grade
catalog admin, identity-sync company settings, and so on), and without
needing to think of "employee" as one value among several
`ppl_person.category` can hold.

**This app owns no data.** Every read/write goes through
`apps/manage-my-people`'s existing API
(`/api/v1/opzhub/manage-my-people/*`); there is no `OPZHR` database, no
new backend controller, and no DB schema YAML file in this drop — the
"design-only, except DB schema YAML" instruction that applies to every
other app this session has nothing to except here, because there is
nothing to add. See
[doc 33 §2](../architecture/33-hr-wrapper-design.md#2-why-a-wrapper-with-no-schema-is-still-worth-designing)
for why this is still worth its own design record rather than a
one-line mention elsewhere.

**What this app is reserved for, later:** goals/OKRs, recognition/
rewards, recruitment/ATS, and workplace-only attendance elaborations
(geofenced check-in/out, payroll rollups, comp-off) are real, evidenced
HRMS sub-domains (same `opz-hrms` research behind
`apps/manage-my-people`) that are genuinely employee-only, unlike
attendance/leave. When one of those gets its own design pass, it belongs
in **this** app — see
[doc 27 §4](../architecture/27-people-domain-design.md#4-explicitly-out-of-scope).

---

## Entities & Tables

None. This app has no logical database and no schema files. Every
entity an HR screen touches — `ppl_person`, `ppl_org_unit`,
`ppl_role_grade`, `ppl_attendance_record`, `ppl_leave_type`,
`ppl_leave_policy`, `ppl_leave_balance`, `ppl_leave_request`,
`ppl_leave_approval`, `ppl_holiday_calendar`,
`ppl_holiday_calendar_day` — is owned and defined by
[apps/manage-my-people](manage-my-people.md).

---

## API Endpoints

None new. This app calls `apps/manage-my-people`'s existing endpoints
directly (`POST /persons/read` with `category=employee` pre-applied,
`POST /attendance/check-in`, `POST /leave-requests`, etc. — see
[manage-my-people.md](manage-my-people.md#api-endpoints)). No gateway,
proxy, or aggregation layer sits in between; the HR frontend is a
same-origin, same-session caller like any other page in the SPA shell.

---

## SQL Commands

None. No database, no commands.

---

## Files to Create

```
apps/manage-my-hr/
├── module.yaml                                              ← NEW (design only, not created this drop)
│                                                                requires: [manage-my-people] — see Dependencies
├── backend/                                                  ← NOT NEEDED (no controllers/services/repositories)
├── db/                                                       ← NOT NEEDED (no schema, no commands)
├── frontend/
│   ├── index.ts
│   ├── routes.tsx                                            (routes wrap manage-my-people's pages with category='employee' locked)
│   ├── menu.ts                                                (HR-branded menu: People, Attendance, Leave)
│   ├── manageMyHrConstants.ts                                (HR-specific labels only — e.g. "Employees" instead of "People")
│   └── pages/
│       ├── HrDashboardPage.tsx                                (HR-flavored landing page; composes manage-my-people widgets)
│       ├── EmployeeDirectoryPage.tsx                           (thin wrapper: <PersonDirectoryPage category="employee" /> from manage-my-people)
│       ├── EmployeeAttendancePage.tsx                          (thin wrapper over manage-my-people's attendance pages)
│       └── EmployeeLeavePage.tsx                               (thin wrapper over manage-my-people's leave pages)
└── mobile/
    ├── plugin.dart
    └── pages/
        └── employee_directory_page.dart                       (thin wrapper, same pattern)
```

No `backend/` Java, no `db/schema/`, no `db/commands/` — listed above as
explicitly not needed, not omitted by oversight.

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| Every screen in this app operates only on `ppl_person` rows with `category = 'employee'` | Frontend routes pass `category=employee` as a fixed, non-editable filter into `apps/manage-my-people`'s existing list/detail calls — never a client-side post-filter of an unscoped result set |
| This app never writes a field `apps/manage-my-people` doesn't already define | No new DTOs, no new columns — enforced by construction (there is no backend here to add one to) |
| An HR user's effective permissions are exactly their `manage-my-people` grants | No new role, no new `id_role_permission` rows under a second `module_id` — see RBAC in DB |

---

## Dependencies

- **`apps/manage-my-people` — REQUIRED, not optional.** This app has no
  data of its own; every screen it renders is `apps/manage-my-people`
  data with a `category = 'employee'` filter applied. See
  [doc 33 §3](../architecture/33-hr-wrapper-design.md#3-dependency-required-not-optional)
  for how this required dependency is enforced (frontend/routing layer,
  not a cross-database data reference — there is no `manage-my-hr`
  database to reference from).
- `identity` module — session and RBAC only, indirectly, through
  whatever grants the logged-in user already holds against
  `module_id = "manage-my-people"`. No schema change, no new role.
- `modules/apps` — this app already has a catalog entry
  (`app_key: manage-my-hr`, `opz-003`, category `hr`) in
  `modules/apps/db/seed/application_catalog.yaml`; no seed change is
  needed. `ApplicationFolderPresence` still requires
  `apps/manage-my-hr/{frontend,backend,mobile}` to exist as directories
  before the app can be licensed — `backend/`/`mobile/` would need at
  least a placeholder even though neither holds real code, matching the
  convention every other catalog app's empty scaffold already uses. Not
  performed in this drop — implementation only.
- **Not designed in this drop:** a platform-level `requires:` field on
  `module.yaml` that would let the installer refuse to license
  `manage-my-hr` without `manage-my-people` already present. Today
  `ApplicationFolderPresence` only checks that `manage-my-hr`'s own
  three folders exist, not that a *different* app is installed — noted
  here as a real gap, same as it was noted (and left undesigned) for
  every required-dependency case this session.

---

## GUI Metadata Design

No new `id_field_definition` rows, no new `form_id`s. Every form this
app renders **is** `apps/manage-my-people`'s own form, unchanged —
`manage-my-people.person.create`, `manage-my-people.attendance-record.edit`,
`manage-my-people.leave-request.create`, and so on
(see [manage-my-people.md § GUI Metadata Design](manage-my-people.md#gui-metadata-design)).
The only thing this app's frontend adds is:

- **Fixed field value, not new metadata:** `category` is pre-filled and
  locked to `employee` on every create screen reached through this
  app's routes — implemented as a route-level default passed into the
  existing `<Form>` component, **not** a new `id_field_definition` row
  and **not** a second copy of the person-create form. The underlying
  field is still exactly as mandatory/masked/role-scoped as
  `apps/manage-my-people` already defines it.
- **Relabeling, not new fields:** where an HR-specific heading reads
  better ("Employee Directory" instead of "People Directory"), that is a
  frontend constant (`manageMyHrConstants.ts`), never a duplicate
  `field_heading` row for the same `field_key`.

### Metadata-Driven Rules

- If a future company wants an HR-specific *required* field that
  shouldn't apply to other `ppl_person.category` values, that is still
  an `apps/manage-my-people` change (a `role_visibility`/`when`
  condition keyed on `category = 'employee'` within the existing form),
  not a field invented in this app.

---

## Directory Placement

```
apps/manage-my-hr/              ← Application-specific (sold, licensed app)
│                                   No logical database — see doc 33 §1
├── frontend/
│   └── pages/                  ← Thin wrappers composing apps/manage-my-people's own pages/components
├── mobile/
│   └── pages/                  ← Same, Flutter side
├── backend/                    ← Empty placeholder only (ApplicationFolderPresence requires the folder)
└── db/                         ← Does not exist — no schema, no commands

apps/manage-my-people/          ← Owns every entity, endpoint, form, and RBAC grant this app uses
```

No changes to `common/frontend/`, `modules/identity/`, or
`modules/apps/` are required.

**Rules:**
- This app never reimplements a field control, a list/detail page
  structure, or a form — it composes `apps/manage-my-people`'s existing
  React components with a fixed `category` prop, the same reusability
  discipline [doc 22](../architecture/22-common-fields-forms-fk.md) §10
  applies to `common/frontend` primitives, one level up: an app-specific
  wrapper reuses another app's screens instead of forking them.

---

## Constants

### Backend

None — no backend package exists for this app.

### Frontend (`apps/manage-my-hr/frontend/manageMyHrConstants.ts`)

```typescript
export const HR_API_BASE               = "/api/v1/opzhub/manage-my-people"; // same base as manage-my-people — no separate gateway
export const HR_LOCKED_CATEGORY        = "employee";
export const HR_DASHBOARD_HEADING      = "HR Dashboard";
export const EMPLOYEE_DIRECTORY_HEADING = "Employee Directory";
export const EMPLOYEE_ATTENDANCE_HEADING = "Employee Attendance";
export const EMPLOYEE_LEAVE_HEADING     = "Employee Leave";
```

No status/enum constants of any kind — this app has no fields it
defines; every allowed value it renders comes from
`apps/manage-my-people`'s `id_field_definition` rows via `FormEnvelope`.
Colors, spacing, and icon keys stay in
`common/frontend/src/theme/tokens.ts`/`common/frontend/src/icons/` — the
`hr` icon key already exists in the catalog, no new icon needed
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).

---

## Optimization, Performance & Memory

### Performance
- No new queries, no new indexes — every list/detail call this app
  makes is `apps/manage-my-people`'s own paginated, filtered endpoint
  with `category=employee` added to the existing filter set, so it
  benefits from every optimization already designed there (cached
  `ppl_org_unit`/`ppl_role_grade`/`ppl_holiday_calendar_day` lookups,
  maintained `ppl_leave_balance` columns, atomic code-sequence
  reservation).
- No additional network hop: this app's frontend calls
  `apps/manage-my-people`'s API directly, not through an aggregation or
  proxy layer that would add latency for no benefit.

### Memory
- No new backend process, no new connection pool, no new cache
  namespace — there is nothing here that could leak.

### Optimization
- Nothing to optimize beyond what `apps/manage-my-people` already does
  — this app adds a UI lens, not a data or compute layer.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> HR wrapper design: [doc 33](../architecture/33-hr-wrapper-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-hr/` — but expect
very few: there is no service/repository layer to unit test. What
belongs here:

| Class | What it tests |
|-------|--------------|
| `EmployeeDirectoryPageTest` (or equivalent frontend test) | The `category` filter passed into `apps/manage-my-people`'s list call is always `employee`, never client-overridable |
| Route-guard test | Every route under `apps/manage-my-hr` requires the same `manage-my-people` RBAC grants its underlying page would — no bypass introduced by wrapping |

No `AttendanceServiceTest`/`LeaveRequestServiceTest`-style backend test
suite — those already exist under
`managemyopz-testing/01-unit/apps/manage-my-people/` and are not
duplicated here.

### RBAC in DB

**No new rows.** This app introduces no `module_id`, no feature ids, and
seeds no roles — see
[doc 33 §4](../architecture/33-hr-wrapper-design.md#4-rbac--no-new-module-no-new-feature-ids).
An HR-specific role is simply a company-defined role (or a copy of
`apps/manage-my-people`'s starter `people_manager`/`people_self` roles)
granted `manage-my-people` feature permissions
(`per`, `att`, `lvr`, `lva`, ...) — created through the existing identity
admin UI.

### Form Metadata in DB

No new `form_id`s. See GUI Metadata Design above.

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| *(all)* | Whatever `apps/manage-my-people`'s endpoint already requires | Whatever `apps/manage-my-people`'s endpoint already requires | Whatever `apps/manage-my-people`'s endpoint already requires |

There is no separate table for this app because there is no separate
enforcement — `@RequiresPermission` annotations live on
`apps/manage-my-people`'s controllers, and this app's frontend simply
calls them. A `manage-my-hr` route never bypasses, weakens, or
duplicates that check.

### Coding Standards (this feature)

**Java:** None — no backend package.

**Flutter:** `ManageMyHrConstants` class (headings/labels only) in
`apps/manage-my-hr/mobile/lib/constants/`, if the mobile wrapper needs
distinct copy from `apps/manage-my-people`'s own Flutter pages.

**TypeScript/React:** `manageMyHrConstants.ts` holds labels only — no
option arrays, no status enums (§ Constants). Pages under
`apps/manage-my-hr/frontend/pages/` import and compose
`apps/manage-my-people`'s exported page components rather than
re-declaring their JSX.

### Directory Confirmation

```
apps/manage-my-hr/
    frontend/pages/   ← thin wrappers around apps/manage-my-people's own pages, category='employee' locked
    mobile/pages/      ← same, Flutter side
    backend/           ← empty (ApplicationFolderPresence placeholder only)
    db/                ← does not exist
apps/manage-my-people/
    (unchanged)        ← owns every table, endpoint, form, and RBAC grant this app uses
```
