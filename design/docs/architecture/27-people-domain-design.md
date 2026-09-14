# 27 — People Domain Design (`apps/manage-my-people`)

This document is **design only** — see [design/docs/requirement/manage-my-people.md](../requirement/manage-my-people.md)
for the implementation-ready requirement, and [apps/manage-my-people/db/schema/](../../../apps/manage-my-people/db/schema/)
for the DB schema YAML files (the one exception to "design only" in this drop).
This app's tables live in their own logical database, `OPZPEOPLE`, per the
platform-wide rule in [doc 28](28-per-application-database-design.md) — every
`company_id → company_information` reference below is therefore a
**cross-database** reference (`OPZPEOPLE` → `OPZMAIN`), enforced at the
application layer, never a Postgres `FOREIGN KEY`.

Reference source: `refer_mmo/opz-hrms` (an existing, not-cleanly-structured HRMS
prototype) was read to extract *requirements*, not copied. No code, package
name, or table name from that app is reused as-is.

## 1. Why this exists

`opz-hrms` hard-codes "employee" into every table, DTO, and screen. That is
fine for a single HR product, but this platform sells **50+ applications**
from one kernel ([00](00-system-overview.md) §1), and the same "a company
has people, each in some org structure, with documents/skills/history"
shape recurs for HR, HRA-style benefit administration, and student
management. Building it three times (three sets of tables, three sets of
screens) violates [22](22-common-fields-forms-fk.md)'s reuse principle at
the domain-model level, not just the field-kit level.

`apps/manage-my-people` is the **generic person master**: one app, sold on
its own (`category: data`-like catalog entry, its own license/install
state — [20](20-licensing-site-central.md)), that owns the `ppl_person`
aggregate and everything that hangs off a person (affiliation, documents,
identifiers, skills, timeline). Future wrapper apps —
`manage-my-hra`, `manage-my-students` — **compose** with it by referencing
`ppl_person.id` and adding their own domain tables, the same way
`inv_move.item_id` references a `master-data` item without forking the
item table ([22](22-common-fields-forms-fk.md) §6.1).

**Why an app and not a module:** `modules/<name>` is for non-application
features every installed app can use (identity, CRUD engine, licensing).
`apps/<name>` is a sold, licensable product in its own right
([Rule 5](../requirement/IMPLEMENTATION_RULES.md#rule-5--directory-structure-common--modules--apps)).
People data is itself something a customer buys and installs — it has a
catalog entry, a license state, an install/uninstall lifecycle
(`modules/apps` — `CompanyApplicationService`) — so it belongs under
`apps/`, not `modules/`. Reuse for HRA/Students happens through **data
composition** (foreign keys + shared metadata), never through one app
importing another's Java package
([02](02-repository-folder-structure.md) §11 rule 2: "Module → module is
forbidden").

## 2. Vocabulary: no "employee" anywhere

| `opz-hrms` term | `manage-my-people` term | Why |
|---|---|---|
| Employee / EmployeeTwin | **Person** (`ppl_person`) | Generic subject; `category` column says what kind. |
| `employee_code` | `person_code` | Same generator pattern (`ppl_person_code_sequence`), generic name. |
| `employment_status` | `status` on `ppl_person` | Values renamed to lifecycle-neutral terms (see requirement doc). |
| Organization/BusinessUnit/Division/Department/Team/Location/CostCenter (6 tables) | **`ppl_org_unit`** (1 table, `unit_type` column) | §3.1 — table-count reduction, not just renaming. |
| Designation/Grade/Band/EmploymentType (4 tables) | **`ppl_role_grade`** (1 table, `grade_type` column) | Same reasoning. |
| `manager_id` / `skip_manager_id` / `mentor_id` / `buddy_id` / `hrbp_id` (5 columns) | **`ppl_person_affiliation`** rows with `relation_type` | §3.2 — relationship rows instead of parallel nullable FKs; gives history for free. |
| `pan_number` / `aadhaar_number` / `uan_number` / `esic_number` / `passport_number` (5 fixed columns) | **`ppl_person_identifier`** (`identifier_type` + value) | Country-neutral; a new ID scheme is a data row, not a migration. |
| `emergency_contact_{1,2,3}_*` (9 fixed columns) | **`ppl_person_emergency_contact`** child rows | Unlimited contacts, not a fixed slot count. |
| `EmployeeDocument` + `Document` + `DocumentVersion` (3 entities) | **`ppl_person_document`** (`version_no` column) | One table, no version parent/child graph. |
| Recruitment ATS, Attendance, Leave, Goals/OKR, Recognition, Workflow engine, Notification | **Out of scope** (§4) | Not part of the person master; belong to consuming apps/modules. |

## 3. Domain model

Full column-level detail is in the requirement doc and the schema YAML
files. This section covers the two generalization decisions that reduce
table count versus the reference app.

### 3.1 `ppl_org_unit` and `ppl_role_grade` — hierarchy as data, not schema

`opz-hrms` (`platform-org-dna`) ships six tables for the org hierarchy and
four for role/grade catalogs, each with near-identical CRUD, soft-delete,
and audit code. `ppl_org_unit` is one self-referencing table
(`parent_unit_id`) with a `unit_type` discriminator; `ppl_role_grade` is
one flat catalog with a `grade_type` discriminator. A `manage-my-students`
wrapper reuses both by inserting rows with `unit_type = INSTITUTION`,
`CAMPUS`, `CLASS` and `grade_type = PROGRAM_LEVEL` — **zero schema
change**. Allowed `unit_type` / `grade_type` values are themselves
metadata-governed (§5), so extending the list for a wrapper app is a data
change, not a migration, consistent with
[22](22-common-fields-forms-fk.md) §4.1's catalog-vs-effective split.

### 3.2 `ppl_person_affiliation` — relationships as rows

`EmployeeTwin` in the reference app carries `manager_id`, `skip_manager_id`,
`department_head_id`, `hrbp_id`, `mentor_id`, `buddy_id` as six parallel
nullable UUID columns directly on the employee row, with no history. This
design uses one child table, `ppl_person_affiliation`, where
`relation_type` picks which kind of relationship a row represents and
`effective_from` / `effective_to` gives every reassignment a history for
free (a transfer no longer overwrites who a person's manager used to be).
`ppl_person.manager_person_id` is kept as a denormalized read of the
current `PRIMARY` row's manager, purely so list screens and the RBAC
ABAC check (`resource.manager_person_id == user.person_id`) don't need a
join on the hot path — it must always be written in the same transaction
as the affiliation row it mirrors (service-layer invariant, not a DB
trigger — [14](14-performance-memory.md) prefers explicit application code
over hidden trigger logic).

### 3.3 What stays a fixed column vs. what becomes a child table

Fixed columns on `ppl_person` are kept only for fields that are (a)
single-valued, (b) queried/filtered/sorted on directory list screens, and
(c) not itself an open-ended catalog. Everything else — identifiers, bank
accounts, emergency contacts, documents, skills, certifications, timeline,
onboarding — is a child table. This is the same test doc 22 §6.2 applies
to foreign keys: normalize when the "many" side is real, don't normalize
speculatively (there is deliberately **no** `ppl_person_address` child
table — current/permanent address stay as two text columns because a
person realistically has exactly two, not an open list).

## 4. Explicitly out of scope

Goals/OKRs, recognition/rewards, the recruitment/ATS pipeline, and a
generic workflow/approval engine all exist in `opz-hrms` but are **not**
part of `manage-my-people`. Reasons:

1. They are genuinely workplace-specific process domains — a goal cycle,
   a recognition/points economy, and a recruitment pipeline don't apply
   to every category `ppl_person.category` might hold (a `student` row
   has no OKRs). Folding them in would turn one sold app into the whole
   HR suite, defeating plug-and-play packaging
   ([01](01-plugin-play-model.md), [10](10-solution-composition.md)).
2. `manage-my-hr` already has a catalog entry
   (`modules/apps/db/seed/application_catalog.yaml`, `app_key: manage-my-hr`,
   `opz-003`) reserved for exactly this kind of employee-only process
   functionality, once one of these is actually designed. **It currently
   has no unique schema of its own** — attendance and leave, which used
   to be scoped there, moved here instead (§5); a future `manage-my-hr`
   build for goals/recognition/recruitment should *consume* `ppl_person`
   (logical reference, `person_id` column, no cross-app Postgres
   `FOREIGN KEY` per [22](22-common-fields-forms-fk.md) §6.2) rather than
   re-implement person data, the same as any other wrapper app.
3. `opz-hrms` itself shows the failure mode of not doing this: its
   recruitment sub-module ships a **second**, duplicate
   `WorkflowDefinition/Step/Assignment` set instead of reusing
   `platform-workflow`. A generic workflow/approval engine, if built for
   this platform, belongs in `modules/` (non-application-specific,
   [Rule 5](../requirement/IMPLEMENTATION_RULES.md#rule-5--directory-structure-common--modules--apps))
   so every consuming app shares one implementation — that is a separate,
   future design, not part of this drop.

## 5. Attendance & leave: category-agnostic, not employee-only

Attendance and leave/time-off also exist in `opz-hrms`, and an earlier
draft of this design scoped both to a separate `apps/manage-my-hr`
wrapper app alongside goals/recognition/recruitment (§4). That was a
mistake, corrected here: **attendance and leave are not workplace-
specific** the way goals/recognition/recruitment are. A student has
attendance and leave/absence too — `manage-my-students`, a wrapper app
this design already exists to support (§1), needs exactly the same
shape. Grouping all five HRMS sub-domains together and deferring them
uniformly ignored that only three of the five are genuinely tied to
employment.

### 5.1 What moved here, and what stayed out

The reference's attendance/leave tables split cleanly into a
category-agnostic core and workplace-only elaborations:

| Reference concept | Disposition |
|---|---|
| Present/absent/late/half-day/on-leave status per person per date | **In scope** — `ppl_attendance_record`. Applies to an employee or a student identically. |
| Leave type, policy, entitlement, balance, multi-level approval, holiday calendar | **In scope** — `ppl_leave_type`/`ppl_leave_policy`/`ppl_leave_policy_assignment`/`ppl_leave_balance`/`ppl_leave_request`/`ppl_leave_approval`/`ppl_holiday_calendar`/`ppl_holiday_calendar_day`. A leave request/approval/balance shape is the same whether the person is staff or a student. |
| Geofenced check-in/check-out (`BiometricDevice`, `EmployeeFaceData`, `QrAttendanceSession`, office-location coordinates), working/late/early-leave minute computation | **Deferred, not built here.** These are workplace-desk elaborations on top of the generic status — a student doesn't clock in/out of an office. Real, evidenced (same `opz-hrms` research as §4), but not forced into this core. |
| `PayrollAttendanceSummary`, `PayrollLeaveTransaction` | **Deferred.** Payroll integration is itself a future, unbuilt dependency (same class as every external-integration gap this session) — not attempted speculatively. |
| `CompOffRequest`/`CompOffWallet` (compensatory time off for working a holiday) | **Deferred.** A genuinely employment-specific concept (working a scheduled off-day) with no student analogue — stays out, same reasoning as goals/recognition/recruitment. |
| `LeavePolicy`/`LeavePolicyRule`/`LeavePolicyVersion`/`LeavePolicyAudit` (a full versioned-policy engine) | **Simplified, not fully built.** `ppl_leave_policy` collapses this to one parameterized table — real policy versioning is deferred, not evidenced as an immediate need beyond what the platform's kernel audit already gives every row change ([Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal)'s avoid-unnecessary-complexity principle). |
| `LeaveRequest.status` values `PENDING_L1`/`PENDING_L2`/`PENDING_L3` (multi-level approval baked into an enum) | **Redesigned.** `ppl_leave_approval` uses one row per approval level (`sequence_no`) instead — the same minimal, app-owned approval-table pattern already established for `dsk_ticket_approval` (`apps/manage-my-desk`) and `fin_write_off` (`apps/manage-my-finance`), with the same forward-looking caveat: migrate to a shared `modules/workflow` if one is ever built (§4 point 3). |

### 5.2 A same-database benefit from the move

Because `ppl_attendance_record`/`ppl_leave_*` now live in `OPZPEOPLE`
alongside `ppl_person`/`ppl_org_unit` (rather than a separate `OPZHR`
database, as an earlier draft had it), `person_id` and
`applies_to_org_unit_id` are **real, Postgres-enforced foreign keys** —
not the cross-database, application-only-enforced references every
other `apps/manage-my-*` app needs for its person/org-unit links
([doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)).
Only `marked_by_user_id`/`approver_user_id` stay cross-database, to
`id_user` on `OPZUSER` — the always-present shared-module case, same as
every other app.

## 6. GUI metadata — confirmed mechanism, no new table

Every `manage-my-people` field-level requirement (mandatory, allowed
values, format, type, heading, subactions, role visibility) is served by
the **existing** `id_field_definition` table
([26](26-rbac-db-design.md) §2) through the **existing** FormEnvelope API
([22](22-common-fields-forms-fk.md) §3) — this app does not define a
parallel metadata table. Concretely:

- `ppl_org_unit.unit_type`, `ppl_role_grade.grade_type`,
  `ppl_person.category`, `ppl_person.status`, and every other "allowed
  values" comment in the schema YAML files point at an
  `id_field_definition.allowed_values` JSONB row, not a DB `CHECK`
  constraint enumerating strings — new values are a data insert.
- Custom, per-company fields (§3.3, EAV via `ppl_person_custom_field`) are
  *defined* as `id_field_definition` rows under
  `form_id = "manage-my-people.person.custom"` and *valued* as
  `ppl_person_custom_field` rows — one metadata mechanism for both the
  fixed schema and the dynamic one, per [26](26-rbac-db-design.md)'s own
  design intent (`field_key` need not map to a physical column).
- Full form-by-form field tables are in the requirement doc's "GUI
  Metadata Design" section, matching the format already used by
  `company-setup.md` and `user-settings.md`.

**Direct answer to "are format/mandatory sections in DB":** yes, confirmed
by [Rule 3](../requirement/IMPLEMENTATION_RULES.md#rule-3--format--mandatory-in-db-confirmed)
and doc 26 §2 — `is_mandatory`, `format_pattern`, `max_length`,
`min_length`, `allowed_values` all live in `id_field_definition`, read
through `FormEnvelope`, mirrored in Java DTO annotations, with a DB
`NOT NULL`/`CHECK` as the last-resort fourth layer. This app follows that
four-layer stack without exception (see requirement doc's "Form Metadata
in DB" section for the concrete `form_id` list and example rows).

## 7. RBAC / ABAC

Feature ids declared for `module_id = "manage-my-people"` (≤4 chars,
[18](18-identity-rbac-abac-oauth2.md) §3.1, cap 32/app):

| Feature id | Title | Covers |
|---|---|---|
| `per` | Person profile | `ppl_person` core fields |
| `aff` | Affiliation | `ppl_person_affiliation` |
| `org` | Org & role catalog | `ppl_org_unit`, `ppl_role_grade` (admin) |
| `idn` | Identifiers | `ppl_person_identifier` (sensitive) |
| `bnk` | Bank accounts | `ppl_person_bank_account` (sensitive) |
| `doc` | Documents | `ppl_person_document` |
| `skl` | Skills & certifications | `ppl_skill_catalog`, `ppl_person_skill`, `ppl_person_certification` |
| `onb` | Onboarding | `ppl_onboarding_step` |
| `tml` | Timeline | `ppl_person_timeline` (read-only; written by services, never by direct user write) |
| `cfg` | Company people settings | `ppl_company_setting` (`identity_sync_mode`; admin-only, §8) |
| `att` | Attendance | `ppl_attendance_record` |
| `lvt` | Leave policy | `ppl_leave_type`, `ppl_leave_policy`, `ppl_leave_policy_assignment` (admin) |
| `lvr` | Leave requests | `ppl_leave_request` |
| `lva` | Leave approval | `ppl_leave_approval` |
| `hol` | Holidays | `ppl_holiday_calendar`, `ppl_holiday_calendar_day` (admin) |

Enforcement is the standard two-layer stack from
[Rule 4](../requirement/IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level):
`@RequiresPermission(module = "manage-my-people", feature = <id>, action = <letter>)`
on every controller method (RBAC — coarse, app+feature+action), plus
`accessService.requireAbac("manage-my-people", <feature>, <letter>, Map.of(...), auth)`
in the service layer for row scoping (ABAC — e.g. a manager may `u` only
rows where `resource.manager_person_id == user.person_id`; a self-service
user may `v` only their own `ppl_person` row). `att`/`lvr`'s `v`/`c`
grants follow the same self-service pattern: scoped to
`resource.person_id == linked_person(user.id)` (§8); `lva`'s `u` grant is
scoped to `resource.approver_user_id == user.id`. The full permission
matrix and starter role seed rows are in the requirement doc's "RBAC in
DB" section — this app introduces no new RBAC *mechanism*, only new
`id_role_permission` / `id_user_permission` rows against the existing five
tables from [26](26-rbac-db-design.md) §2.

**No dependency on `identity` schema changes.** An earlier draft of this
document assumed self-service screens would need a new `id_user.person_id`
column owned by `modules/identity`. That is no longer necessary: the link
lives entirely on this app's own side (`ppl_person.linked_user_id`, §8),
which is the correct owner per [doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
the app that needs the reference stores the foreign id, the referenced
app's schema is untouched.

## 8. Login-user link and per-company identity sync

A person is **optionally** connected to a login account —
`ppl_person.linked_user_id`, a nullable cross-database logical reference
to `id_user.id` on `OPZUSER` (doc 28 §3.2). Two things are true at once
and must not be conflated:

- **Not every person has a login.** Contractors, HRA members, students in
  a `manage-my-students` wrapper may be tracked here with `linked_user_id
  = null` indefinitely — this app works fully without `identity` beyond
  session auth for the *operator* editing the records.
- **Not every company wants linked data to be the same data.** Once
  `linked_user_id` is set, whether `ppl_person.first_name` /
  `last_name` / `display_name` / `work_email` are *reused from* the login
  account or stay *independently editable* is a per-company choice —
  `ppl_company_setting.identity_sync_mode` (`LINKED` \| `INDEPENDENT`,
  default `INDEPENDENT`). Some customers run one identity for both login
  and HR-style records (`LINKED`); others keep HR data authoritative on
  its own even for people who do have a login (`INDEPENDENT`) — e.g. a
  company that provisions accounts before HR finishes onboarding, or
  whose HR system of record predates its login system.

**Resolution, not duplication:** `GET /persons/me` resolves the session's
`user_id` to a person via `ppl_person.linked_user_id` (indexed, unique —
one login maps to at most one person) — it does not read a cached copy of
`id_user`. When `identity_sync_mode = LINKED`, `PersonService` calls
identity's own registered command (e.g. `identity.find_user_by_id`) to
pull current name/email at the moment of link and on an explicit
"Sync from login" subaction — never a silent background job overwriting a
person's fields, and never a copy of `id_user` columns stored redundantly
on `ppl_person` beyond what was just synced into the normal name/email
columns it already has. While `LINKED`, those fields render
`role_visibility: readonly` on the Person Profile screen (metadata-driven,
§6) rather than being enforced by a second, separate mechanism.

Admin control of `identity_sync_mode` is its own small screen/feature
(`cfg` — Company People Settings, admin-only; see requirement doc RBAC
table).

## 9. Performance, optimization, memory (domain-specific notes)

Beyond the universal rules in
[Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal):

- `ppl_org_unit` and `ppl_role_grade` are read-heavy, write-rare catalogs
  (used to populate every person form's lookup fields). Cache them in
  `CacheClient` per company with an explicit TTL, evicted on write — do
  not re-query on every form open.
- `ppl_person_timeline` and `ppl_person_custom_field` are unbounded-growth
  child tables (one grows per lifecycle event, one grows per custom field
  per person). Directory/list screens must never join them; they are
  loaded only when a single person's detail screen is opened, paginated
  on the timeline side.
- `ppl_person_identifier.identifier_value` and
  `ppl_person_bank_account.account_number` are excluded from the generic
  CRUD API (`expose_generic_api: false`) and from list/collection
  projections — same pattern `company-setup.md` uses for `db_password` /
  `be_api_token`. A dedicated, narrowly-scoped command/endpoint serves the
  single-record read for a user holding the `idn`/`bnk` `v` grant.
- Directory (person list) screens are always server-paginated
  (`ppl_person` can reach tens of thousands of rows per company) with
  filters pushed to SQL — never `SELECT *` then filter client-side
  ([Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal)).
- `linked_user_id` resolution (`GET /persons/me`) is cached per session
  (`CacheClient`, session-scoped TTL) after the first resolve — not
  re-queried against `OPZUSER` on every request, per the cross-app
  caching rule in [doc 28 §3.1](28-per-application-database-design.md#31-the-rule).
- `ppl_leave_balance`'s maintained columns mean a leave-request form's
  "available balance" check never runs a live aggregate over
  `ppl_leave_request` at render time.
- `ppl_holiday_calendar_day` is a read-heavy, write-rare table (used by
  every leave-request `days_count` computation) — cached per company
  alongside `ppl_org_unit`/`ppl_role_grade`, same TTL/eviction discipline.
- `ppl_attendance_record` is a high-write-volume, one-row-per-person-per-
  day table; the unique `(person_id, attendance_date)` index doubles as
  the natural "today's attendance" lookup — no separate covering index
  needed.

## 10. What must not happen

- A second field-metadata table for this app — use `id_field_definition`.
- A second RBAC table or a JSONB permissions column on `ppl_person` — use
  the five `id_*` tables from doc 26.
- Goals, recognition, recruitment, or a generic workflow engine
  implemented inside `apps/manage-my-people` (§4) — attendance and leave
  are the exception, in scope for the reasons in §5.
- Workplace-only attendance/leave elaborations (geofenced check-in/out
  coordinates, biometric capture, payroll rollups, compensatory-off)
  added to this app's schema instead of staying deferred (§5.1) — they
  have no student analogue and belong to a future, evidenced,
  employee-specific design.
- A wrapper app (`manage-my-hra`, `manage-my-students`) forking
  `ppl_person` into its own copy instead of adding rows/child tables that
  reference it.
- Fixed emergency-contact or government-ID columns re-appearing on
  `ppl_person` "for convenience" — they were normalized out deliberately
  (§2, §3.3).
- `identifier_value` / `account_number` returned in any list/collection
  response.
- This app's tables declared under `database: OPZMAIN` instead of
  `OPZPEOPLE`, or a Postgres `FOREIGN KEY` attempted across `OPZPEOPLE`
  and `OPZMAIN`/`OPZUSER` ([doc 28](28-per-application-database-design.md)).
- A cached local copy of `id_user` fields on `ppl_person` beyond what
  `identity_sync_mode = LINKED` explicitly synced — no shadow replica of
  identity data "for convenience."
- A silent background job overwriting `LINKED` person fields from
  `id_user` — sync only happens on link and on the explicit "Sync from
  login" subaction.
- Unit test files under `apps/manage-my-people/**/src/test/` — they
  belong in `managemyopz-testing/01-unit/` (see requirement doc).
