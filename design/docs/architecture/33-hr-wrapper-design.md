# 33 — HR Wrapper App Design (`apps/manage-my-hr`)

This document is **design only**. There is no DB schema exception this
time — `apps/manage-my-hr` adds **zero new tables** in this drop. That is
the central point of this document, not an omission: everything this app
needs already exists in `apps/manage-my-people`
([27](27-people-domain-design.md)), because attendance and leave — the
two HRMS sub-domains this app's catalog description names — turned out
to be category-agnostic, not employee-only, and were folded into
`apps/manage-my-people` instead (see
[doc 27 §5](27-people-domain-design.md#5-attendance--leave-category-agnostic-not-employee-only)
for the full reasoning and the correction history).

This document exists so `apps/manage-my-hr` has a proper design record —
matching every other catalog app this session — rather than being a
one-line mention inside `apps/manage-my-people`'s own docs.

## 1. What this app is, right now

`apps/manage-my-hr` is a **thin, category-scoped presentation wrapper**
over `apps/manage-my-people`:

- No tables of its own. `ppl_person`, `ppl_org_unit`, `ppl_role_grade`,
  `ppl_attendance_record`, `ppl_leave_*`, `ppl_holiday_calendar*` all
  live in `apps/manage-my-people`'s `OPZPEOPLE` database and stay there.
- No new backend controllers/services. Every read/write goes through
  `apps/manage-my-people`'s existing API
  (`/api/v1/opzhub/manage-my-people/*`).
- What it *does* provide: an "HR" branded menu, dashboard, and routes
  that default and lock `category = 'employee'` — so an HR user sees a
  people directory, attendance view, and leave console scoped to
  employees, without the generic cross-category admin surface
  `apps/manage-my-people` itself exposes (org-unit/role-grade catalog
  admin, identity-sync company settings, etc.), and without needing to
  understand that "employee" is one value among several
  `ppl_person.category` can hold.
- Still its own catalog entry (`app_key: manage-my-hr`, `opz-003`,
  category `hr`) — a company can buy/install "the HR experience"
  as a distinct product, the same way `manage-my-market` is a distinct
  purchase even though nothing here changes that model. It is simply
  **functionally inert without `apps/manage-my-people` installed** — see
  §3.

## 2. Why a wrapper with no schema is still worth designing

Three reasons this isn't "nothing to do here":

1. **It's the concrete demonstration of the reuse story.** Doc 27 was
   designed from the start to support wrapper apps
   ([doc 27 §1](27-people-domain-design.md#1-why-this-exists)). This app
   is proof the design works as intended — zero duplicated data, zero
   duplicated RBAC, a real sellable product assembled entirely from
   composition.
2. **It's the reserved home for genuinely employee-only HRMS
   sub-domains.** Goals/OKRs, recognition/rewards, recruitment/ATS, and
   the workplace-only attendance elaborations (geofenced check-in/out,
   payroll rollups, comp-off) are real, evidenced (same `opz-hrms`
   research as doc 27) but deliberately not built yet
   ([doc 27 §4](27-people-domain-design.md#4-explicitly-out-of-scope)).
   When one of those gets its own design pass, **this is the app it
   belongs to** — `apps/manage-my-hr` gains its first real table at that
   point, not before.
3. **The negative space needs to be on record.** Without this document,
   a future contributor could reasonably assume `manage-my-hr` needs its
   own person/attendance/leave tables (the natural first guess for an
   "HR app") and rebuild what doc 27 already generalized — exactly the
   duplication this whole design set exists to prevent.

## 3. Dependency: required, not optional

Same required-dependency shape originally documented for this app when
attendance/leave were (incorrectly) scoped here directly: this app has
no data of its own, so it cannot function — and should not be
licensable — without `apps/manage-my-people` already installed. The
enforcement mechanism doesn't change: `apps/manage-my-hr`'s frontend
calls `apps/manage-my-people`'s API directly (same-origin, same session,
same RBAC matrix — not a cross-database data reference at all, since
there is no `manage-my-hr` database to reference *from*). What
`apps/manage-my-people`'s other wrapper apps do at the data layer
([doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)),
this app does at the frontend/routing layer instead — there being no
`OPZHR` database is the point, not a gap.

## 4. RBAC — no new module, no new feature ids

`apps/manage-my-hr` does **not** register its own `module_id`. There is
nothing for a separate RBAC namespace to protect — every resource an HR
user touches (`ppl_person`, `ppl_attendance_record`, `ppl_leave_request`,
...) already has `id_role_permission` rows under `module_id =
"manage-my-people"` (feature ids `per`, `att`, `lvr`, `lva`, ... —
[doc 27 §7](27-people-domain-design.md#7-rbac--abac)). An "HR" role is
simply a company-defined role (or a copy of `people_manager`/
`people_self`) granted exactly those `manage-my-people` feature
permissions — created through the existing identity admin UI, not
seeded by this app's own migration, because this app has no migration.

## 5. What must not happen

- A `ppl_person`-shaped table (or any copy of `apps/manage-my-people`
  data) added to a new `OPZHR` database "to give manage-my-hr its own
  home" — there is no `OPZHR` database in this design.
- A parallel `module_id = "manage-my-hr"` RBAC namespace duplicating
  grants that already exist under `manage-my-people` (§4).
- Attendance/leave logic reimplemented here instead of calling
  `apps/manage-my-people`'s existing endpoints.
- Goals/recognition/recruitment tables added here speculatively instead
  of through their own future, evidenced design pass (§2 point 2).
