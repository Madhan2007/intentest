# Requirement: Manage My People

**App:** `manage-my-people` (new, `apps/manage-my-people`)
**DB target:** `opzpeople` (per-company, own logical database — [doc 28](../architecture/28-per-application-database-design.md))
**Access level:** Role-based per feature (see RBAC in DB section) — no single fixed role
**Architecture ref:** [doc 27](../architecture/27-people-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md) · [doc 26 §2](../architecture/26-rbac-db-design.md) · [doc 22](../architecture/22-common-fields-forms-fk.md) · [doc 25](../architecture/25-common-features-design.md)
**Reference source:** `refer_mmo/opz-hrms` (requirements extracted only — no code, package, or table name reused)

---

## What It Does

A generic **person master and digital "people twin"**: profile, org
affiliation, lifecycle status/history, documents, government/compliance
identifiers, bank accounts, emergency contacts, skills, certifications,
and an onboarding checklist — for any kind of person a company tracks, not
only employees. The word "employee" does not appear anywhere in this
app's schema, code, or UI copy; the generic term is **person**, and
`ppl_person.category` says what kind of person a row is (`employee`,
`student`, `member`, `contractor`, ...).

This app is the **foundation** other apps build on: a future
`manage-my-hra` (benefits/insurance administration) or
`manage-my-students` (student management) references `ppl_person.id` and
adds its own domain tables — it does not get its own copy of name/contact/
org-structure/document tables. `manage-my-hr` (already reserved in the app
catalog, `opz-003`) is expected to do the same for attendance, leave,
goals, recognition, and recruitment — none of which are in this app. See
[doc 27 §4](../architecture/27-people-domain-design.md#4-explicitly-out-of-scope)
for the full out-of-scope list and reasoning.

**Design-only drop:** per the user's instruction, this requirement and its
architecture doc are design artifacts. The **only** files actually created
in this drop are the DB schema YAML files under
[`apps/manage-my-people/db/schema/`](../../../apps/manage-my-people/db/schema/).
Everything else in "Files to Create" below is implementation for later.

---

## Vocabulary Mapping

See [doc 27 §2](../architecture/27-people-domain-design.md#2-vocabulary-no-employee-anywhere)
for the full table. Summary: `Employee` → **Person**; six org-structure
tables → **one** `ppl_org_unit` (`unit_type` discriminator); four
role/grade tables → **one** `ppl_role_grade` (`grade_type`
discriminator); five manager-type FK columns → **rows** in
`ppl_person_affiliation` (`relation_type`); five fixed compliance-ID
columns → **rows** in `ppl_person_identifier` (`identifier_type`); three
fixed emergency-contact slots → **rows** in
`ppl_person_emergency_contact`.

---

## Entities & Tables

All tables are in `opzpeople` — this app's **own** logical database, per
the per-application-database rule in
[doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application),
not the shared `opzmain`. `company_id` and `linked_user_id` references
below are therefore **cross-database** (to `opzmain` and `opzuser`
respectively), enforced at the application layer per
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
never a Postgres `FOREIGN KEY`. Full column definitions, indexes, and
foreign keys are the schema YAML files under
`apps/manage-my-people/db/schema/` — this section is the human-readable
summary.

### `ppl_person` — core person record

| Column | Type | Required | Notes |
|--------|------|---------|-------|
| `id` | uuid | auto | PK |
| `company_id` | uuid | Yes | FK → `company_information` |
| `person_code` | text | Yes | Unique per company; generated via `ppl_person_code_sequence` |
| `category` | text | Yes | `employee` \| `student` \| `member` \| `contractor` \| `custom`; default `employee` |
| `first_name` / `middle_name` / `last_name` / `display_name` | text | first/last/display required | Name parts |
| `date_of_birth`, `gender`, `nationality`, `marital_status`, `blood_group`, `preferred_language`, `avatar_url` | mixed | No | Personal details |
| `work_email`, `personal_email`, `work_phone`, `personal_phone` | text | No | Unique per company on `work_email` |
| `current_address`, `permanent_address` | text | No | Free text |
| `status` | text | Yes | `ACTIVE` \| `ON_HOLD` \| `ON_LEAVE` \| `SUSPENDED` \| `EXITED` \| `ARCHIVED`; default `ACTIVE` |
| `joined_on`, `confirmed_on`, `probation_end_date`, `notice_period_days`, `exit_date`, `exit_reason` | mixed | No | Lifecycle dates |
| `manager_person_id` | uuid | No | Denormalized mirror of the current `PRIMARY` `ppl_person_affiliation` row's manager |
| `linked_user_id` | uuid | No | Optional cross-database logical link to `id_user.id` (`opzuser`); unique when set. See "Login-User Link & Identity Sync" below |
| `created_by`/`created_at`/`updated_by`/`updated_at` | mixed | auto | Standard audit columns |

### `ppl_company_setting` — per-company identity sync configuration

`company_id` (PK), `identity_sync_mode` (`LINKED` \| `INDEPENDENT`,
default `INDEPENDENT`), `updated_by`, `updated_at`. One row per company;
created with the default on company provisioning. See "Login-User Link &
Identity Sync" below.

### `ppl_org_unit` — generic org hierarchy

`id`, `company_id`, `unit_type` (`ORGANIZATION` \| `BUSINESS_UNIT` \|
`DIVISION` \| `DEPARTMENT` \| `SUB_DEPARTMENT` \| `TEAM` \| `LOCATION` \|
`COST_CENTER` \| `CUSTOM`), `parent_unit_id` (self-FK, nullable),
`unit_code`, `unit_name`, `is_active`, `created_at`.

### `ppl_role_grade` — generic role/grade catalog

`id`, `company_id`, `grade_type` (`DESIGNATION` \| `GRADE` \| `BAND` \|
`EMPLOYMENT_TYPE` \| `PROGRAM_LEVEL` \| `CUSTOM`), `grade_code`,
`grade_name`, `rank_order`, `is_active`.

### `ppl_person_affiliation` — org placement + relationships

`id`, `person_id`, `org_unit_id`, `role_grade_id`, `cost_center_unit_id`,
`position_title`, `manager_person_id`, `relation_type` (`PRIMARY` \|
`SECONDARY` \| `MENTOR` \| `BUDDY` \| `HRBP` \| `SKIP_MANAGER`),
`effective_from`, `effective_to`, `is_current`.

### `ppl_person_identifier` — compliance/government IDs

`id`, `person_id`, `identifier_type` (`NATIONAL_ID` \| `TAX_ID` \|
`SOCIAL_INSURANCE_ID` \| `PASSPORT` \| `DRIVING_LICENSE` \| `CUSTOM`),
`identifier_value` (sensitive), `issuing_country`, `expires_on`,
`is_verified`. `expose_generic_api: false`.

### `ppl_person_bank_account`

`id`, `person_id`, `bank_name`, `account_number` (sensitive),
`routing_code`, `branch_name`, `is_primary`. `expose_generic_api: false`.

### `ppl_person_emergency_contact`

`id`, `person_id`, `contact_name`, `relation`, `phone`, `priority_order`.

### `ppl_person_document`

`id`, `person_id`, `document_category`, `document_name`, `file_url`,
`file_size_bytes`, `mime_type`, `version_no`, `status` (`ACTIVE` \|
`ARCHIVED`), `uploaded_by`, `uploaded_at`.

### `ppl_skill_catalog` / `ppl_person_skill`

Catalog: `id`, `company_id`, `skill_name`, `skill_category`, `is_active`.
Link: `id`, `person_id`, `skill_id`, `proficiency_level` (`BEGINNER` \|
`INTERMEDIATE` \| `ADVANCED` \| `EXPERT`).

### `ppl_person_certification`

`id`, `person_id`, `certification_name`, `issuing_authority`,
`credential_id`, `issued_on`, `expires_on`, `credential_url`,
`is_verified`.

### `ppl_person_custom_field` — EAV values (definitions live in `id_field_definition`)

`id`, `person_id`, `field_key`, `field_value`, `field_group`,
`display_order`. See [doc 27 §6](../architecture/27-people-domain-design.md#6-gui-metadata--confirmed-mechanism-no-new-table).

### `ppl_person_timeline` — read-only lifecycle log

`id`, `person_id`, `event_type`, `event_date`, `title`, `description`,
`metadata_json`, `triggered_by_person_id`, `created_at`. Written only by
services reacting to other writes (promotion, transfer, exit, ...) — never
a direct user create/update/delete.

### `ppl_onboarding_step`

`id`, `person_id`, `step_code`, `step_status` (`PENDING` \|
`IN_PROGRESS` \| `COMPLETED` \| `SKIPPED`), `completed_at`.

### `ppl_person_code_sequence`

`id`, `company_id`, `prefix`, `next_number`. Company + prefix scoped only
(see schema YAML comment for why no org-unit scope column).

### `ppl_attendance_record` — category-agnostic daily attendance

`id`, `company_id`, `person_id` (real, same-database FK — see
[doc 27 §5.2](../architecture/27-people-domain-design.md#52-a-same-database-benefit-from-the-move)),
`attendance_date`, `status` (`PRESENT` \| `ABSENT` \| `LATE` \|
`HALF_DAY` \| `ON_LEAVE` \| `EXCUSED`), `marked_by_user_id` (null =
self-marked), `notes`. Unique per `(person_id, attendance_date)`.
Deliberately excludes check-in/check-out timestamps, geofencing, and
working-minutes computation — see
[doc 27 §5.1](../architecture/27-people-domain-design.md#51-what-moved-here-and-what-stayed-out)
for why those stay a future, employee-specific design rather than
forced into this generic core.

### `ppl_leave_type` / `ppl_leave_policy` / `ppl_leave_policy_assignment`

Type: `id`, `company_id`, `leave_type_code`, `leave_type_name`,
`is_paid`, `is_active`. Policy: `id`, `company_id`, `leave_type_id`,
`policy_name`, `annual_entitlement_days`, `carry_forward_max_days`,
`accrual_frequency` (`MONTHLY` \| `ANNUAL` \| `NONE`), `is_active`.
Assignment: `id`, `leave_policy_id`, `applies_to_org_unit_id` (real FK
to `ppl_org_unit`; null = company-wide).

### `ppl_leave_balance`

`id`, `company_id`, `person_id`, `leave_type_id`, `period_year`,
`entitled_days`, `carried_forward_days`, and the maintained
`used_days`/`pending_days`/`balance_days` — never client-writable, see
Business Rules.

### `ppl_leave_request` / `ppl_leave_approval`

Request: `id`, `company_id`, `person_id`, `leave_type_id`, `start_date`,
`end_date`, `is_half_day`, `half_day_session`, `days_count`, `reason`,
`status` (`DRAFT` \| `PENDING` \| `APPROVED` \| `REJECTED` \|
`CANCELLED`), `submitted_at`, `decided_at`. Approval: `id`,
`leave_request_id`, `sequence_no`, `approver_user_id`, `status`
(`PENDING` \| `APPROVED` \| `REJECTED`), `comments`, `decided_at` — one
row per approval level, replacing a fixed `PENDING_L1`/`L2`/`L3` status
enum.

### `ppl_holiday_calendar` / `ppl_holiday_calendar_day`

Calendar: `id`, `company_id`, `calendar_name`, `is_default`,
`is_active`. Day: `id`, `calendar_id`, `holiday_date`, `holiday_name`,
`is_optional`.

---

## Login-User Link & Identity Sync

Full rationale: [doc 27 §8](../architecture/27-people-domain-design.md#8-login-user-link-and-per-company-identity-sync).
Summary for implementation:

- `ppl_person.linked_user_id` is **optional** — most rows may never have
  one (contractors, students, HRA members with no login).
- Linking does **not** require `identity` schema changes — the reference
  lives entirely on `ppl_person`, resolved cross-database at read time
  (`doc 28`), never copied wholesale.
- `ppl_company_setting.identity_sync_mode` controls only whether
  first/last/display name and work email are **pulled from** the linked
  login account:
  - `INDEPENDENT` (default): person fields are always directly editable;
    linking a user account is purely a reference, no field copies.
  - `LINKED`: name/email fields are copied from `id_user` at link time
    and on an explicit "Sync from login" subaction only — never a
    background job. Those fields render read-only on the Person Profile
    screen while linked (`role_visibility`, GUI Metadata section below).
- `GET /persons/me` resolves `session.user_id → ppl_person.linked_user_id`
  and is the only endpoint allowed to make that resolution implicitly;
  everywhere else, linking/unlinking is an explicit action
  (`POST /persons/{id}/link-user`, `DELETE /persons/{id}/link-user`).

---

## API Endpoints

Base path: `/api/v1/opzhub/manage-my-people`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/persons` | Create a person |
| `POST` | `/persons/read` | List persons (paginated, filterable by `category`, `status`, `org_unit_id`) or read one by `id` |
| `PUT` | `/persons` | Update a person |
| `DELETE` | `/persons` | Archive a person (`status → ARCHIVED`) — **not** a hard delete while child rows exist |
| `GET` | `/persons/me` | Current session user's own person record, resolved via `ppl_person.linked_user_id` |
| `POST` | `/persons/{id}/link-user` | Link a person to a login account (`{ "user_id": "..." }`); applies `identity_sync_mode` if `LINKED` |
| `DELETE` | `/persons/{id}/link-user` | Unlink; person fields are untouched (never cleared) |
| `POST` | `/persons/{id}/sync-from-login` | Explicit re-sync of name/email from the linked login account (only valid when `identity_sync_mode = LINKED` and a link exists) |
| `GET` / `PUT` | `/company-settings` | Read/update this company's `ppl_company_setting` (`identity_sync_mode`) — `cfg` feature, admin-only |
| `GET` | `/persons/code/preview` | Preview the next `person_code` for a prefix, without reserving it |
| `POST` | `/persons/code/reserve` | Reserve and return the next `person_code` |
| `POST` | `/persons/{id}/affiliations` | Add an affiliation/relationship row |
| `POST` | `/persons/{id}/affiliations/read` | List affiliation history for a person |
| `PUT` | `/affiliations` | Update (close out) an affiliation row (`effective_to`, `is_current`) |
| `POST` | `/org-units` / `/org-units/read` / `PUT /org-units` / `DELETE /org-units` | Org hierarchy CRUD |
| `POST` | `/role-grades` / `/role-grades/read` / `PUT /role-grades` / `DELETE /role-grades` | Role/grade catalog CRUD |
| `POST` | `/persons/{id}/identifiers` / `/persons/{id}/identifiers/read` / `PUT /identifiers` / `DELETE /identifiers` | Compliance ID CRUD (list response excludes `identifier_value`) |
| `POST` | `/persons/{id}/bank-accounts` / `.../read` / `PUT /bank-accounts` / `DELETE /bank-accounts` | Bank account CRUD (list response excludes `account_number`) |
| `POST` | `/persons/{id}/emergency-contacts` / `.../read` / `PUT` / `DELETE` | Emergency contact CRUD |
| `POST` | `/persons/{id}/documents` / `.../read` / `PUT` / `DELETE` | Document CRUD (upload via kernel document/storage port) |
| `POST` | `/skills` / `/skills/read` | Skill catalog CRUD (admin) |
| `POST` | `/persons/{id}/skills` / `.../read` / `DELETE` | Person ↔ skill link |
| `POST` | `/persons/{id}/certifications` / `.../read` / `PUT` / `DELETE` | Certification CRUD |
| `POST` | `/persons/{id}/custom-fields` / `.../read` | Custom field values (definitions come from `GET /forms/manage-my-people.person.custom`) |
| `GET` | `/persons/{id}/timeline` | Read-only timeline (paginated) |
| `POST` | `/persons/{id}/onboarding-steps` / `.../read` / `PUT` | Onboarding checklist |
| `POST` | `/attendance/check-in` / `PUT /attendance/check-out` | Self-service attendance for the session's linked person |
| `POST` | `/attendance/read` | List attendance records (paginated, filterable by `person_id`, `attendance_date`, `status`) |
| `POST` | `/leave-types` / `.../read` / `PUT` / `DELETE` | Leave type CRUD (admin) |
| `POST` | `/leave-policies` / `.../read` / `PUT` / `DELETE` | Policy CRUD (admin) |
| `POST` | `/leave-policies/{id}/assignments` / `.../read` / `DELETE` | Policy-to-org-unit assignment |
| `GET` | `/leave-balances` | Balances for the current person, or (manager/admin) a given `person_id` |
| `POST` | `/leave-requests` | Submit a leave request |
| `POST` | `/leave-requests/read` | List requests (paginated, filterable by `status`, `person_id`) |
| `PUT` | `/leave-requests/{id}/cancel` | Cancel a `DRAFT`/`PENDING` request |
| `PUT` | `/leave-requests/{id}/approvals/{sequenceNo}` | Approve/reject one approval level |
| `POST` | `/holiday-calendars` / `.../read` / `PUT` / `DELETE` | Calendar CRUD (admin) |
| `POST` | `/holiday-calendars/{id}/days` / `.../read` / `DELETE` | Holiday date CRUD (admin) |

All responses use the standard `ApiEnvelope<T>` wrapper with
`correlation_id`, matching every other module.

---

## SQL Commands

Named SQL files in `apps/manage-my-people/db/commands/`, one file per
query (same convention as `modules/identity/db/commands/`), e.g.:

```
person.insert.sql
person.find_by_id.sql
person.find_by_company_and_code.sql
person.list_paged.sql
person.update.sql
person.archive.sql
person.find_by_user_id.sql              -- backs GET /persons/me (WHERE linked_user_id = :user_id)
person.link_user.sql                    -- backs POST /persons/{id}/link-user
person.unlink_user.sql                  -- backs DELETE /persons/{id}/link-user
company_setting.find_by_company.sql     -- backs GET /company-settings; default row if missing
company_setting.upsert.sql              -- backs PUT /company-settings
affiliation.insert.sql
affiliation.find_current_by_person.sql
affiliation.close_current.sql
org_unit.list_by_type.sql
role_grade.list_by_type.sql
person_identifier.find_by_person_and_type.sql
person_bank_account.find_primary_by_person.sql
person_document.list_by_person.sql
person_skill.list_by_person.sql
person_custom_field.upsert.sql
person_timeline.insert.sql
person_timeline.list_by_person_paged.sql
person_code_sequence.reserve_next.sql   -- single UPDATE ... RETURNING, no read-then-write race
onboarding_step.upsert_status.sql
attendance_record.upsert_check_in.sql / .upsert_check_out.sql / .find_by_person_and_date.sql / .list_paged.sql
leave_type.list_by_company.sql
leave_policy.insert.sql / .update.sql / .list_by_company.sql
leave_policy_assignment.insert.sql / .list_by_policy.sql
leave_balance.find_by_person_type_year.sql / .recompute.sql   -- recount from leave_request rows for one balance row
leave_request.insert.sql / .cancel.sql / .list_paged.sql
leave_approval.insert.sql / .decide.sql / .list_by_request.sql
holiday_calendar.list_by_company.sql / holiday_calendar_day.list_by_calendar.sql
```

`leave_balance.recompute.sql` must run inside the same transaction as
the triggering request's status change (submit/approve/reject/cancel) —
never a separate, eventually-consistent step.

`person_code_sequence.reserve_next.sql` must be a single atomic
`UPDATE ppl_person_code_sequence SET next_number = next_number + 1 WHERE
company_id = :company_id AND prefix = :prefix RETURNING next_number`
(insert-on-missing via `ON CONFLICT`) — never a `SELECT` followed by an
`UPDATE`, to avoid a race under concurrent person creation.

---

## Files to Create

```
apps/manage-my-people/
├── module.yaml                                              ← NEW (design only, not created this drop)
├── backend/src/main/java/com/managemyopz/apps/managemypeople/
│   ├── ManageMyPeopleAutoConfiguration.java                 ← NEW
│   ├── api/
│   │   ├── PersonController.java
│   │   ├── AffiliationController.java
│   │   ├── OrgUnitController.java
│   │   ├── RoleGradeController.java
│   │   ├── PersonIdentifierController.java
│   │   ├── PersonBankAccountController.java
│   │   ├── PersonDocumentController.java
│   │   ├── SkillController.java
│   │   ├── PersonTimelineController.java
│   │   ├── OnboardingStepController.java
│   │   ├── CompanySettingController.java
│   │   ├── AttendanceController.java
│   │   ├── LeaveTypeController.java
│   │   ├── LeavePolicyController.java
│   │   ├── LeaveBalanceController.java
│   │   ├── LeaveRequestController.java
│   │   ├── HolidayCalendarController.java
│   │   └── dto/                                              (Create/Update/Response DTOs per entity)
│   ├── application/
│   │   ├── PersonService.java
│   │   ├── PersonIdentitySyncService.java                    (link-user, unlink, sync-from-login; cross-app calls to identity)
│   │   ├── AffiliationService.java
│   │   ├── OrgUnitService.java
│   │   ├── RoleGradeService.java
│   │   ├── PersonCodeSequenceService.java
│   │   ├── PersonTimelineService.java                        (writes timeline rows on other services' events)
│   │   ├── CompanySettingService.java
│   │   ├── AttendanceService.java
│   │   ├── LeavePolicyService.java
│   │   ├── LeaveBalanceService.java                          (maintained-column recomputation)
│   │   ├── LeaveRequestService.java
│   │   ├── LeaveApprovalService.java
│   │   ├── HolidayCalendarService.java
│   │   └── ManageMyPeopleConstants.java
│   ├── domain/
│   │   ├── Person.java
│   │   ├── OrgUnit.java
│   │   ├── RoleGrade.java
│   │   ├── Affiliation.java
│   │   └── ... (one record per entity)
│   └── data/
│       ├── PersonRepository.java / DataClientPersonRepository.java
│       └── ... (one repository pair per entity)
├── db/
│   ├── schema/                                               ← EXISTS (this drop — 25 files)
│   │   ├── ppl_person.yaml
│   │   ├── ppl_org_unit.yaml
│   │   ├── ppl_role_grade.yaml
│   │   ├── ppl_person_affiliation.yaml
│   │   ├── ppl_person_identifier.yaml
│   │   ├── ppl_person_bank_account.yaml
│   │   ├── ppl_person_emergency_contact.yaml
│   │   ├── ppl_person_document.yaml
│   │   ├── ppl_skill_catalog.yaml
│   │   ├── ppl_person_skill.yaml
│   │   ├── ppl_person_certification.yaml
│   │   ├── ppl_person_custom_field.yaml
│   │   ├── ppl_person_timeline.yaml
│   │   ├── ppl_onboarding_step.yaml
│   │   ├── ppl_person_code_sequence.yaml
│   │   ├── ppl_company_setting.yaml
│   │   ├── ppl_attendance_record.yaml
│   │   ├── ppl_leave_type.yaml
│   │   ├── ppl_leave_policy.yaml
│   │   ├── ppl_leave_policy_assignment.yaml
│   │   ├── ppl_leave_balance.yaml
│   │   ├── ppl_leave_request.yaml
│   │   ├── ppl_leave_approval.yaml
│   │   ├── ppl_holiday_calendar.yaml
│   │   └── ppl_holiday_calendar_day.yaml
│   └── commands/                                             ← NEW (see SQL Commands above)
├── forms/                                                    ← NEW (catalog defaults, doc 22 §7)
│   ├── person.create.yaml
│   ├── person.edit.yaml
│   ├── person.list.yaml
│   ├── affiliation.edit.yaml
│   ├── org-unit.edit.yaml
│   ├── role-grade.edit.yaml
│   ├── person-identifier.edit.yaml
│   ├── person-bank-account.edit.yaml
│   ├── person-document.edit.yaml
│   ├── person-skill.edit.yaml
│   ├── person-certification.edit.yaml
│   ├── person-custom.yaml
│   ├── onboarding-step.edit.yaml
│   ├── company-setting.edit.yaml
│   ├── leave-request.create.yaml
│   ├── leave-type.edit.yaml
│   ├── leave-policy.edit.yaml
│   ├── attendance-record.edit.yaml
│   └── holiday-calendar-day.edit.yaml
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   ├── menu.ts
│   ├── manageMyPeopleConstants.ts
│   └── pages/
│       ├── PersonDirectoryPage.tsx
│       ├── PersonProfilePage.tsx                             (tabs: profile, affiliation, documents, identifiers, bank, skills, timeline, onboarding)
│       ├── OrgUnitAdminPage.tsx
│       ├── RoleGradeAdminPage.tsx
│       ├── SkillCatalogAdminPage.tsx
│       ├── CompanyPeopleSettingsPage.tsx                     (identity_sync_mode toggle; admin-only)
│       ├── AttendanceCheckInPage.tsx                          (self-service)
│       ├── AttendanceTeamViewPage.tsx                         (manager)
│       ├── LeaveRequestPage.tsx                                (self-service apply)
│       ├── LeaveApprovalInboxPage.tsx                          (manager)
│       ├── LeavePolicyAdminPage.tsx
│       └── HolidayCalendarAdminPage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        ├── person_directory_page.dart
        ├── person_profile_page.dart
        ├── attendance_check_in_page.dart
        └── leave_request_page.dart
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| `person_code` unique per company, generated (not user-typed) | `ppl_person_code_sequence.reserve_next.sql`; DB unique index as final guard |
| `work_email` unique per company when present | DB unique index; 409 on conflict |
| A person cannot be hard-deleted while any child row exists | Service checks before delete; DB `RESTRICT`/`CASCADE` per schema YAML is the last-resort guard |
| Deleting a person is always an archive (`status = ARCHIVED`), never a physical `DELETE` | `PersonService.archive()` — the `DELETE /persons` endpoint calls this, not a SQL `DELETE` |
| Only one `ppl_person_affiliation` row per person may have `relation_type = PRIMARY` and `is_current = true` | Service closes the previous `PRIMARY` row (`effective_to = today`, `is_current = false`) in the same transaction as inserting the new one |
| `ppl_person.manager_person_id` must always mirror the current `PRIMARY` affiliation's `manager_person_id` | Written in the same transaction as the affiliation change (see [doc 27 §3.2](../architecture/27-people-domain-design.md#32-ppl_person_affiliation--relationships-as-rows)) |
| `ppl_org_unit` / `ppl_role_grade` rows in use by any `ppl_person_affiliation` cannot be deleted | FK `RESTRICT`; surface as 409 Conflict |
| `identifier_value` / `account_number` never appear in list/collection responses | Repository-level projection excludes the column on list queries (same pattern as `company-setup`'s `db_password`) |
| `ppl_person_timeline` rows are never created, updated, or deleted directly by a user-facing endpoint | No `POST /persons/{id}/timeline` write endpoint exists; only `GET` |
| Custom field keys not registered in `id_field_definition` (`form_id = manage-my-people.person.custom`) are rejected on write | `PersonCustomFieldService` validates `field_key` against the FormEnvelope before upsert |
| A login account (`id_user.id`) may be linked to at most one person | DB unique index on `ppl_person.linked_user_id`; 409 on attempted second link |
| `identity_sync_mode = LINKED` never auto-overwrites a person's name/email outside of link time or the explicit "Sync from login" action | `PersonIdentitySyncService` — no scheduler, no event listener performing silent sync |
| Unlinking a person from a login account never clears previously-synced name/email fields | `link-user`/`unlink-user` only touch `linked_user_id`, never the name/email columns |
| Every table's `company_id` (and `ppl_person.linked_user_id`) is validated against the owning app's database before write, never assumed present | Service-layer existence check per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication), same `Conflict`/`err: fk` shape as a same-database FK |
| A person may have only one `ppl_attendance_record` per calendar day | DB unique index on `(person_id, attendance_date)`; service upserts on check-in |
| `ppl_leave_balance.used_days`/`pending_days`/`balance_days` are never client-writable | Excluded from any update DTO; only `LeaveBalanceService` recomputes them, in the same transaction as a leave request's status change |
| A leave request cannot exceed the current `balance_days` unless the leave type permits negative balance | `LeaveRequestService.submit()` — v1 always blocks; per-type override is a future iteration |
| Each `ppl_leave_approval` level must be decided in `sequence_no` order | `LeaveApprovalService` rejects an out-of-order decision |
| A leave request's overall `status` becomes `APPROVED` only once every `ppl_leave_approval` row for it is `APPROVED` | `LeaveApprovalService` |

---

## Dependencies

- `identity` module — session, RBAC tables (`id_role`, `id_user_role`,
  `id_role_permission`, `id_user_permission`, `id_field_definition` from
  [doc 26](../architecture/26-rbac-db-design.md)), and a registered named
  command this app calls cross-database (`identity.find_user_by_id`,
  `opzuser`) for `GET /persons/me` resolution and `identity_sync_mode =
  LINKED` syncs. **No schema change to `identity` is required** — the
  link is owned entirely by `ppl_person.linked_user_id` on this app's own
  side, per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication).
- `company-setup` — `company_information` (`opzmain`) must exist for the
  cross-database `company_id` reference on every table in this app.
- **New platform capability:** `OPZPEOPLE` as a provisioned logical
  database (connection pool, migration target, backup schedule) —
  [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).
  This is infrastructure, not a schema dependency on another app.
- `modules/apps` — this app must be added to
  `modules/apps/db/seed/application_catalog.yaml` (new `app_key:
  manage-my-people`, next `product_code`, a new `icon_key`) before it can
  be licensed/installed through the existing dashboard launcher
  (`CompanyApplicationService` — see the earlier `ApplicationFolderPresence`
  folder-presence check, which also requires
  `apps/manage-my-people/{frontend,backend,mobile}` to all exist). Not
  performed in this drop — implementation only.
- `common/frontend/src/icons/` needs a new `people` icon component, and
  `dashboardConstants.ts`-equivalent icon-key mapping, matching the
  pattern every other catalog `icon_key` already follows.
- **Attendance/leave introduce no new dependency.** Both live on the same
  `OPZPEOPLE` database as the rest of this app and use the same
  cross-database `id_user` references already established for
  `linked_user_id` — see
  [doc 27 §5](../architecture/27-people-domain-design.md#5-attendance--leave-category-agnostic-not-employee-only)
  for why they were folded in here rather than a separate
  `apps/manage-my-hr` wrapper.

---

## GUI Metadata Design

Every screen renders from `id_field_definition` via `GET
/api/v1/opzhub/forms/{form_id}` ([doc 22](../architecture/22-common-fields-forms-fk.md) §3)
— none of the tables below are hardcoded in JSX/Dart. `role_visibility`
and `subactions` columns shown here are illustrative of the
`id_field_definition` rows this app's migration will insert (per
[Rule 3](IMPLEMENTATION_RULES.md#rule-3--format--mandatory-in-db-confirmed)).

### Screen: Person Profile (`manage-my-people.person.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `category` | Category | `select` | Yes | `employee`, `student`, `member`, `contractor`, `custom` (`allowed_values` JSONB) | `per` role with `c` | Locked (readonly) after create |
| `person_code` | Person Code | `text` | Yes (system-set) | Generated; not user-editable on create | `per` any `v` | "Preview code" subaction calls `/persons/code/preview` |
| `first_name` / `last_name` | First Name / Last Name | `text` | Yes | Max 100 chars | `per` `c`/`u`; `role_visibility: {"default":"edit"}`, forced `readonly` when `identity_sync_mode = LINKED` and `linked_user_id` is set | "Sync from login" subaction visible only when linked |
| `display_name` | Display Name | `text` | Yes | Max 200 chars | `per` `c`/`u` | Auto-suggested from first+last, editable |
| `date_of_birth` | Date of Birth | `datetime` | No | Date only, past dates only (`rules.max` = today) | `per` `c`/`u`; `role_visibility: {"default":"edit","self":"readonly"}` | — |
| `work_email` | Work Email | `text` | No | `fmt: email` | `per` `c`/`u`; forced `readonly` when `identity_sync_mode = LINKED` and linked | Live uniqueness check on blur |
| `status` | Status | `radio` | Yes | `ACTIVE`,`ON_HOLD`,`ON_LEAVE`,`SUSPENDED`,`EXITED`,`ARCHIVED` | `per` `u` only (not `c`) | Changing to `ARCHIVED`/`EXITED` shows confirmation dialog |
| `manager_person_id` | Manager | `lookup` | No | `fk: { res: "manage-my-people.person", v: "id", d: "display_name" }` | `per` `u` | Read-only here; changed only via the Affiliation screen |

### Screen: Affiliation (`manage-my-people.affiliation.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `org_unit_id` | Org Unit | `lookup` | Conditional | `fk: { res: "manage-my-people.org-unit" }`; filtered by `unit_type` in `{DEPARTMENT,TEAM,LOCATION}` | `aff` `c`/`u` | — |
| `role_grade_id` | Designation / Grade | `lookup` | Conditional | `fk: { res: "manage-my-people.role-grade" }` | `aff` `c`/`u` | — |
| `relation_type` | Relationship | `select` | Yes | `PRIMARY`,`SECONDARY`,`MENTOR`,`BUDDY`,`HRBP`,`SKIP_MANAGER` | `aff` `c` | Selecting `PRIMARY` auto-closes the previous current `PRIMARY` row (server-side, not client) |
| `manager_person_id` | Manager / Related Person | `lookup` | Conditional | Required when `relation_type != PRIMARY` | `aff` `c`/`u` | — |
| `effective_from` | Effective From | `datetime` | Yes | Date only | `aff` `c` | — |

### Screen: Org Unit Admin (`manage-my-people.org-unit.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `unit_type` | Unit Type | `select` | Yes | `ORGANIZATION`,`BUSINESS_UNIT`,`DIVISION`,`DEPARTMENT`,`SUB_DEPARTMENT`,`TEAM`,`LOCATION`,`COST_CENTER`,`CUSTOM` | `org` `c`/`u` | Locked after create |
| `parent_unit_id` | Parent Unit | `lookup` | No | `fk: { res: "manage-my-people.org-unit" }`; excludes self and descendants | `org` `c`/`u` | Cycle-prevention validated server-side |
| `unit_code` / `unit_name` | Code / Name | `text` | Yes | Code max 32, unique per (`company`,`unit_type`) | `org` `c`/`u` | Uniqueness check on blur |

### Screen: Identifiers (`manage-my-people.person-identifier.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `identifier_type` | ID Type | `select` | Yes | `NATIONAL_ID`,`TAX_ID`,`SOCIAL_INSURANCE_ID`,`PASSPORT`,`DRIVING_LICENSE`,`CUSTOM` | `idn` `c` | — |
| `identifier_value` | ID Value | `password`-style masked text | Yes | Format per `identifier_type` (regex from `format_pattern`) | `idn` `v`/`u` only, never listed | "Reveal" subaction, audited |
| `is_verified` | Verified | `boolean` | No | — | `idn` `u`, `role_visibility: {"default":"readonly","hr_admin":"edit"}` | — |

### Screen: Person Directory (list)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `person_code` | Code | `text` | Yes | `per` `v` |
| `display_name` | Name | `text` | Yes | `per` `v` |
| `category` | Category | `badge` | Yes | `per` `v` |
| `status` | Status | `status-badge` | Yes | `per` `v` |
| `org_unit_name` (joined) | Department/Unit | `text` | No | `per` `v` |
| — | Actions | `actions` | No | `per` `v` | View, Edit (`u`), Archive (`d`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import: []`
(person creation always goes through the code-reservation flow, never
bulk import, to protect `person_code` uniqueness), `bulk: { u: true, d:
false }` (bulk status update allowed; bulk archive requires the
single-record confirmation dialog, not bulk delete).

### Screen: Leave Request (`manage-my-people.leave-request.create`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `leave_type_id` | Leave Type | `lookup` | Yes | `fk: { res: "manage-my-people.leave-type" }` | `lvr` `c` | Selecting shows the current `balance_days` for that type as a hint |
| `start_date` / `end_date` | Start / End Date | `datetime` | Yes | Date only; `end_date >= start_date` | `lvr` `c` | Days spanning a holiday (per the company's `ppl_holiday_calendar`) are excluded from `days_count` automatically |
| `is_half_day` | Half Day | `boolean` | No | true/false | `lvr` `c` | Enables `half_day_session` when true |
| `half_day_session` | Session | `radio` | Conditional | `FIRST_HALF`, `SECOND_HALF` | `lvr` `c` | Visible only when `is_half_day = true` |
| `reason` | Reason | `textarea` | No | Max 1000 chars | `lvr` `c` | — |

### Screen: Attendance Record (`manage-my-people.attendance-record.edit`, manager/admin correction)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `status` | Status | `select` | Yes | `PRESENT`,`ABSENT`,`LATE`,`HALF_DAY`,`ON_LEAVE`,`EXCUSED` | `att` `u`, manager/admin only | — |
| `notes` | Notes | `textarea` | No | Max 500 chars | `att` `u` | — |

### Screen: Attendance Directory (list, manager/admin)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `person_id` (joined display name) | Person | `text` | Yes | `att` `v` |
| `attendance_date` | Date | `date` | Yes | `att` `v` |
| `status` | Status | `status-badge` | Yes | `att` `v` |
| — | Actions | `actions` | No | `att` `v` | View, Edit (`u`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import: []`
(attendance is always captured via check-in or manager correction, never
bulk-imported), `bulk: { u: false, d: false }`.

### Screen: Company People Settings (`manage-my-people.company-setting.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `identity_sync_mode` | Reuse login name/email | `radio` | Yes | `INDEPENDENT` (People data stays independent), `LINKED` (Reuse from login account) | `cfg` `u` (admin only) | Switching to `LINKED` shows a confirmation dialog explaining existing linked persons will offer a "Sync from login" action, not an automatic bulk overwrite |

### Metadata-Driven Rules

- `days_count` on a leave request is always server-computed (excluding
  holidays per the applicable `ppl_holiday_calendar`) — never a
  client-side date-diff the server merely trusts.
- Leave request `status` is never a free-editable field — approval
  happens through the dedicated per-level approve/reject action.
- `half_day_session` visibility is driven by a FormEnvelope `when`
  condition on `is_half_day` ([doc 22 §4.3](../architecture/22-common-fields-forms-fk.md#43-depends-on-still-from-be)),
  not a hardcoded `if` in the component.
- `status` transition to `ARCHIVED` or `EXITED` always shows a
  confirmation dialog (`subactions: [{"action":"confirm","trigger":"submit"}]`)
  — driven by metadata, not a hardcoded `if (status === 'ARCHIVED')` in
  the page component.
- `identifier_value` and `account_number` fields always render masked
  with a `reveal` subaction; the reveal call is itself RBAC/ABAC-checked
  server-side (§ API-Level RBAC/ABAC below), not just hidden in CSS.
- Custom fields (`ppl_person_custom_field`) render from the
  `manage-my-people.person.custom` FormEnvelope — the Person Profile page
  never hardcodes a company's custom field list.

---

## Directory Placement

```
apps/manage-my-people/          ← Application-specific (sold, licensed app)
│                                   Own logical database: OPZPEOPLE (doc 28 §2)
├── backend/
├── frontend/
│   └── pages/                  ← Uses field primitives from common/frontend, not its own copies
├── mobile/
├── forms/                      ← Catalog defaults; runtime = id_field_definition (doc 26) + FormEnvelope (doc 22)
└── db/
    ├── schema/                 ← EXISTS (this drop) — 25 files, all database: OPZPEOPLE
    └── commands/

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reused for org_unit_id, role_grade_id, manager_person_id, skill_id
│   └── ... (no new control types needed — this app introduces no field kind not already in doc 22 §2)
├── theme/tokens.ts               ← Reused; no new colors
└── icons/                        ← One new icon component: "people" (matches this app's icon_key)
```

No change to `modules/identity/` is needed (§ Dependencies) — the login
link is owned by `ppl_person.linked_user_id` on this app's own side,
resolved cross-database per [doc 28](../architecture/28-per-application-database-design.md).

**Rules:**
- No module-local reimplementation of `LookupField`, `DataTable`, or any
  field control — this app is data (forms YAML + `id_field_definition`
  rows), not new UI primitives, matching [doc 22](../architecture/22-common-fields-forms-fk.md) §10.
- `ppl_org_unit` / `ppl_role_grade` lookups are reused by every screen in
  this app via the kernel `LookupField` + `/lookup/{res}` API — never a
  per-screen `<select>` of all rows.

---

## Constants

### Backend (`apps/manage-my-people/backend/.../ManageMyPeopleConstants.java`)

```java
public static final String CATEGORY_EMPLOYEE          = "employee";
public static final String CATEGORY_STUDENT            = "student";
public static final String CATEGORY_MEMBER              = "member";
public static final String CATEGORY_CONTRACTOR          = "contractor";
public static final String CATEGORY_CUSTOM               = "custom";

public static final String STATUS_ACTIVE                = "ACTIVE";
public static final String STATUS_ON_HOLD               = "ON_HOLD";
public static final String STATUS_ON_LEAVE               = "ON_LEAVE";
public static final String STATUS_SUSPENDED              = "SUSPENDED";
public static final String STATUS_EXITED                 = "EXITED";
public static final String STATUS_ARCHIVED               = "ARCHIVED";

public static final String RELATION_TYPE_PRIMARY         = "PRIMARY";
public static final String RELATION_TYPE_SECONDARY       = "SECONDARY";
public static final String RELATION_TYPE_MENTOR          = "MENTOR";
public static final String RELATION_TYPE_BUDDY           = "BUDDY";
public static final String RELATION_TYPE_HRBP            = "HRBP";
public static final String RELATION_TYPE_SKIP_MANAGER    = "SKIP_MANAGER";

public static final int    PERSON_CODE_MAX_LEN           = 32;
public static final int    NAME_MAX_LEN                  = 100;

public static final String ATTENDANCE_STATUS_PRESENT     = "PRESENT";
public static final String ATTENDANCE_STATUS_ABSENT      = "ABSENT";
public static final String ATTENDANCE_STATUS_LATE        = "LATE";
public static final String ATTENDANCE_STATUS_HALF_DAY    = "HALF_DAY";
public static final String ATTENDANCE_STATUS_ON_LEAVE    = "ON_LEAVE";
public static final String ATTENDANCE_STATUS_EXCUSED     = "EXCUSED";

public static final String LEAVE_REQUEST_STATUS_DRAFT     = "DRAFT";
public static final String LEAVE_REQUEST_STATUS_PENDING   = "PENDING";
public static final String LEAVE_REQUEST_STATUS_APPROVED  = "APPROVED";
public static final String LEAVE_REQUEST_STATUS_REJECTED  = "REJECTED";
public static final String LEAVE_REQUEST_STATUS_CANCELLED = "CANCELLED";
```

### Frontend (`apps/manage-my-people/frontend/manageMyPeopleConstants.ts`)

```typescript
export const PEOPLE_API_BASE           = "/api/v1/opzhub/manage-my-people";
export const PERSON_DIRECTORY_HEADING  = "People Directory";
export const PERSON_PROFILE_HEADING    = "Person Profile";
export const CATEGORY_OPTIONS = [
  { value: "employee",   label: "Employee"   },
  { value: "student",    label: "Student"    },
  { value: "member",     label: "Member"     },
  { value: "contractor", label: "Contractor" },
];
export const STATUS_OPTIONS = [
  { value: "ACTIVE",    label: "Active"     },
  { value: "ON_HOLD",   label: "On Hold"    },
  { value: "ON_LEAVE",  label: "On Leave"   },
  { value: "SUSPENDED", label: "Suspended"  },
  { value: "EXITED",    label: "Exited"     },
  { value: "ARCHIVED",  label: "Archived"   },
];
export const LEAVE_STATUS_OPTIONS = [
  { value: "DRAFT",     label: "Draft"     },
  { value: "PENDING",   label: "Pending"   },
  { value: "APPROVED",  label: "Approved"  },
  { value: "REJECTED",  label: "Rejected"  },
  { value: "CANCELLED", label: "Cancelled" },
];
```

Colors, spacing, and icon keys stay in `common/frontend/src/theme/tokens.ts`
and `common/frontend/src/icons/` — not duplicated here
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).

---

## Optimization, Performance & Memory

See [doc 27 §9](../architecture/27-people-domain-design.md#9-performance-optimization-memory-domain-specific-notes)
for the domain-specific reasoning. Summary of concrete rules:

### Performance
- `ppl_org_unit` and `ppl_role_grade` are cached per company in
  `CacheClient` (explicit TTL, evicted on write) — every person form's
  lookups read cache, not Postgres, on the common path.
- Person directory list is always server-paginated with filters
  (`category`, `status`, `org_unit_id`) pushed into
  `person.list_paged.sql` — never fetched whole then filtered client-side.
- `person_code_sequence.reserve_next.sql` is one atomic
  `UPDATE ... RETURNING` — no read-then-write round trip, no race under
  concurrent creates.
- `GET /persons/me` resolution (`session.user_id → linked_user_id`) is
  cached per session (`CacheClient`, session TTL) after first resolve —
  cross-database lookups against `opzuser` are not repeated on every
  request ([doc 28 §3.1](../architecture/28-per-application-database-design.md#31-the-rule)).
- `ppl_leave_balance`'s maintained columns mean a leave-request form's
  "available balance" check never runs a live aggregate over
  `ppl_leave_request`.
- `ppl_holiday_calendar_day` is cached alongside `ppl_org_unit`/
  `ppl_role_grade` (same TTL/eviction discipline) — every leave request's
  `days_count` computation reads cache, not Postgres.
- `ppl_attendance_record`'s unique `(person_id, attendance_date)` index
  doubles as the "today's attendance" lookup — no separate covering
  index needed.

### Memory
- **React:** `PersonProfilePage` tabs (affiliation, documents,
  identifiers, ...) fetch their own tab's data lazily on tab-select, not
  all at once on page load — avoids holding every child collection for
  every open profile in memory.
- **Java:** `PersonTimelineService` streams timeline pages
  (`person_timeline.list_by_person_paged.sql`) — never loads a person's
  full timeline into one list.
- **Flutter:** each profile tab disposes its own controllers on tab
  change; the directory list uses a paginated `ListView.builder`, not an
  in-memory full list.

### Optimization
- `identifier_value` / `account_number` are excluded from every list/
  collection SQL projection at the query level (not filtered after
  fetch) — the sensitive column is never even selected for a list
  response.
- Bulk update on the directory list touches only `mode: edit` fields per
  doc 22 §10.2 (`BulkUpdateDrawer`) — never a blind full-row rewrite.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> People domain design: [doc 27](../architecture/27-people-domain-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-people/`
(mirroring the `01-unit/modules/<name>/` convention from
[Rule 1](IMPLEMENTATION_RULES.md#rule-1--unit-test-cases-separate-repo),
extended one level for the `apps/` vs `modules/` split — see doc 27 §1).
No test files under `apps/manage-my-people/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `PersonServiceTest` | Create/update validation; archive-not-delete; `person_code` reservation atomicity |
| `AffiliationServiceTest` | Only one current `PRIMARY` row per person; `manager_person_id` mirror stays in sync |
| `OrgUnitServiceTest` | Cycle prevention on `parent_unit_id`; delete blocked while referenced |
| `PersonIdentifierServiceTest` | `identifier_value` excluded from list projection; format validation per `identifier_type` |
| `PersonCustomFieldServiceTest` | Write rejected for a `field_key` not present in `id_field_definition` |
| `PersonControllerTest` | RBAC annotation enforcement per feature id; ABAC self/manager row scoping |
| `AttendanceServiceTest` | Check-in/check-out upsert respects the unique `(person_id, attendance_date)` constraint |
| `LeaveRequestServiceTest` | `days_count` excludes holidays; submission blocked when balance insufficient |
| `LeaveApprovalServiceTest` | Approvals must be decided in `sequence_no` order; overall status only `APPROVED` once every level is |
| `LeaveBalanceServiceTest` | Recomputation triggered only by request status transitions, never a direct field write |

### RBAC in DB

Feature ids (full table and rationale: [doc 27 §7](../architecture/27-people-domain-design.md#7-rbac--abac)):
`per`, `aff`, `org`, `idn`, `bnk`, `doc`, `skl`, `onb`, `tml`, `cfg`,
`att`, `lvt`, `lvr`, `lva`, `hol`.

Starter roles seeded by this app's migration (in addition to any
company-defined roles created later through the identity admin UI):

```sql
INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('people_admin',   'People Administrator', true),
  ('people_manager', 'People Manager',       true),
  ('people_self',    'Self Service',         true);

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('people_admin',   'manage-my-people', 'per', 'vcud'),
  ('people_admin',   'manage-my-people', 'aff', 'vcud'),
  ('people_admin',   'manage-my-people', 'org', 'vcud'),
  ('people_admin',   'manage-my-people', 'idn', 'vcud'),
  ('people_admin',   'manage-my-people', 'bnk', 'vcud'),
  ('people_admin',   'manage-my-people', 'doc', 'vcud'),
  ('people_admin',   'manage-my-people', 'skl', 'vcud'),
  ('people_admin',   'manage-my-people', 'onb', 'vcud'),
  ('people_admin',   'manage-my-people', 'tml', 'v'),
  ('people_admin',   'manage-my-people', 'cfg', 'vu'),
  ('people_admin',   'manage-my-people', 'att', 'vcud'),
  ('people_admin',   'manage-my-people', 'lvt', 'vcud'),
  ('people_admin',   'manage-my-people', 'lvr', 'vcud'),
  ('people_admin',   'manage-my-people', 'lva', 'vcu'),
  ('people_admin',   'manage-my-people', 'hol', 'vcud'),
  ('people_manager', 'manage-my-people', 'per', 'vu'),
  ('people_manager', 'manage-my-people', 'aff', 'v'),
  ('people_manager', 'manage-my-people', 'doc', 'vc'),
  ('people_manager', 'manage-my-people', 'tml', 'v'),
  ('people_manager', 'manage-my-people', 'att', 'v'),
  ('people_manager', 'manage-my-people', 'lvr', 'v'),
  ('people_manager', 'manage-my-people', 'lva', 'vu'),
  ('people_self',    'manage-my-people', 'per', 'v'),
  ('people_self',    'manage-my-people', 'doc', 'v'),
  ('people_self',    'manage-my-people', 'tml', 'v'),
  ('people_self',    'manage-my-people', 'att', 'vc'),
  ('people_self',    'manage-my-people', 'lvr', 'vc');
```

`people_manager`'s `per.u` and `aff.v`/`doc.v`/`tml.v`/`att.v`/`lvr.v`
grants are further narrowed by ABAC to rows where
`resource.manager_person_id == user.person_id`; `lva.u` is narrowed to
`resource.approver_user_id == user.id` (§ API-Level RBAC/ABAC).
`people_self` is narrowed to `resource.id == user.person_id` (for
`per`/`doc`/`tml`) or `resource.person_id == user.person_id` (for
`att`/`lvr`). Company admins may layer `id_user_permission` GRANT/REVOKE
rows on top of these three starter roles without creating new roles, per
[Rule 2](IMPLEMENTATION_RULES.md#rule-2--rbac-in-db-with-optimized-tables).

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `manage-my-people.person.create` | Create Person |
| `manage-my-people.person.edit` | Edit Person |
| `manage-my-people.person.list` | Person Directory |
| `manage-my-people.affiliation.edit` | Add/Edit Affiliation |
| `manage-my-people.org-unit.edit` | Org Unit Admin |
| `manage-my-people.role-grade.edit` | Role/Grade Admin |
| `manage-my-people.person-identifier.edit` | Identifiers |
| `manage-my-people.person-bank-account.edit` | Bank Accounts |
| `manage-my-people.person-document.edit` | Documents |
| `manage-my-people.person-skill.edit` | Skills |
| `manage-my-people.person-certification.edit` | Certifications |
| `manage-my-people.person.custom` | Custom Fields (per-company defined) |
| `manage-my-people.onboarding-step.edit` | Onboarding Checklist |
| `manage-my-people.company-setting.edit` | Company People Settings |
| `manage-my-people.leave-request.create` | Apply for Leave |
| `manage-my-people.leave-type.edit` | Leave Type Admin |
| `manage-my-people.leave-policy.edit` | Leave Policy Admin |
| `manage-my-people.attendance-record.edit` | Attendance Correction (manager/admin) |
| `manage-my-people.holiday-calendar-day.edit` | Holiday Calendar Admin |

Example migration row (`category` field, DB-driven allowed values):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, display_order, allowed_values, subactions, role_visibility
) VALUES (
  'manage-my-people.person.create', 'category', 'Category', 'select',
  true, 1,
  '[{"value":"employee","label":"Employee"},{"value":"student","label":"Student"},
    {"value":"member","label":"Member"},{"value":"contractor","label":"Contractor"}]'::jsonb,
  '[{"action":"lock_after_create","trigger":"submit"}]'::jsonb,
  '{"default":"edit"}'::jsonb
);

INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, max_length, display_order
) VALUES (
  'manage-my-people.person.create', 'first_name', 'First Name', 'text',
  true, 100, 2
);
```

`identifier_value` field definition marks itself sensitive via
`role_visibility` rather than a new metadata column, reusing the existing
JSONB shape:

```sql
-- field_key: identifier_value  field_type: password  is_mandatory: true
-- role_visibility: {"default":"hidden","idn_viewer":"edit"}
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /persons` | `SessionAuthFilter` (401) | `per.c` | Tenant-scoped only (`company_id` from session) |
| `POST /persons/read` | 401 | `per.v` | Row filter: managers see only `manager_person_id == user.person_id` rows unless they also hold `org`-level admin |
| `PUT /persons` | 401 | `per.u` | `resource.manager_person_id == user.person_id` OR `resource.id == user.person_id` (self, name/contact fields only — `status`/`category` excluded for self via field `role_visibility`) |
| `DELETE /persons` | 401 | `per.d` | `people_admin` only in practice (no manager/self grant on `d`) |
| `GET /persons/{id}/identifiers` | 401 | `idn.v` | Never returns `identifier_value` unless caller also holds a dedicated reveal grant, checked per-call, logged with `correlation_id` |
| `GET /persons/{id}/bank-accounts` | 401 | `bnk.v` | Same reveal pattern as identifiers |
| `GET /persons/{id}/timeline` | 401 | `tml.v` | Same row scoping as `per.v` |
| `POST /persons/{id}/link-user`, `DELETE .../link-user` | 401 | `per.u` | `people_admin` only in practice (no manager/self grant) — linking is an identity-affecting action |
| `POST /persons/{id}/sync-from-login` | 401 | `per.u` | Same as link-user; also 409 if `identity_sync_mode != LINKED` |
| `GET`/`PUT /company-settings` | 401 | `cfg.v` / `cfg.u` | `people_admin` only |
| `POST /attendance/check-in` | 401 | `att.c` | Always the session's own linked person — `person_id` never taken from the request body |
| `POST /leave-requests` | 401 | `lvr.c` | Always the session's own linked person |
| `PUT /leave-requests/{id}/approvals/{sequenceNo}` | 401 | `lva.u` | `resource.approver_user_id == user.id` for `people_manager`; unscoped for `people_admin` |

Every controller method carries `@RequiresPermission(module =
"manage-my-people", feature = <id>, action = <letter>)`
([Rule 4](IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level)).
ABAC row scoping is evaluated in the service layer via
`accessService.requireAbac(...)` / `accessService.rowFilter(...)`, never
in the controller.

### Coding Standards (this feature)

**Java:** Domain entities (`Person`, `OrgUnit`, `Affiliation`, ...) are
immutable `record`s. All SQL in `apps/manage-my-people/db/commands/`. No
business logic in controllers — delegate to `*Service`. Constants in
`ManageMyPeopleConstants.java` only.

**Flutter:** `ManageMyPeopleConstants` class in
`apps/manage-my-people/mobile/lib/constants/`. All
`TextEditingController` instances disposed in `dispose()`.
`identifier_value`/`account_number` never rendered in plain text without
an explicit reveal action.

**TypeScript/React:** `CATEGORY_OPTIONS`, `STATUS_OPTIONS`, and
`PEOPLE_API_BASE` in `manageMyPeopleConstants.ts`. `LookupField` and
`DataTable` imported from `common/frontend` — not re-implemented. No
inline color/icon literals — `theme/tokens.ts` and `icons/` only.

### Directory Confirmation

```
apps/manage-my-people/
    backend/          ← all controller + service + repository Java
    frontend/          ← pages using common field primitives
    mobile/            ← Flutter pages using common widgets
    forms/             ← catalog defaults compiled into FormEnvelope
    db/schema/         ← 25 schema YAML files (this drop), database: OPZPEOPLE
    db/commands/       ← all SQL named commands (implementation)
common/frontend/src/
    icons/             ← + one new "people" icon component
    theme/tokens.ts    ← reused, unchanged
```

No `modules/identity/` changes required — see Dependencies and
"Login-User Link & Identity Sync" above.

---

## Reuse for Future Wrapper Apps

This section exists because reusability was an explicit design goal, not
an afterthought — restated from [doc 27 §1](../architecture/27-people-domain-design.md#1-why-this-exists).

A future `manage-my-hra` or `manage-my-students`:

1. Does **not** get its own `person`/`employee`/`student` table.
2. Adds `ppl_person.category = 'hra_member'` or `'student'` (a value, not
   a schema change) — allowed by inserting a new
   `id_field_definition.allowed_values` entry for
   `manage-my-people.person.create`'s `category` field.
3. Gets its **own** logical database (`OPZHRA`, `OPZSTUDENTS`, per
   [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application))
   and adds its own domain tables there (e.g.
   `hra_policy_enrollment.person_id`, `student_enrollment.person_id`) with
   a **cross-database logical reference** to `ppl_person.id` on
   `OPZPEOPLE` — never a Postgres `REFERENCES` (impossible across
   databases) and never a copy of person columns into the wrapper's own
   table, per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication).
4. Extends `ppl_org_unit` / `ppl_role_grade` with new `unit_type` /
   `grade_type` values (e.g. `INSTITUTION`, `CLASS`, `PROGRAM_LEVEL`)
   instead of building its own hierarchy tables.
5. Defines its own custom fields via `id_field_definition` rows under its
   own `form_id` (e.g. `manage-my-hra.enrollment.custom`), and may also
   add rows to `manage-my-people.person.custom` if it needs extra fields
   on the shared person record itself.
6. Requests its own `id_role_permission` rows against `module_id =
   "<wrapper-app>"` — it never needs `manage-my-people`'s `people_admin`
   role to function; it composes RBAC the same way it composes data.
7. **Reuses attendance and leave directly, with no schema of its own for
   either.** `manage-my-students` records a student's attendance and
   leave requests via this app's own `ppl_attendance_record`/
   `ppl_leave_request` tables (`person_id` pointing at a row with
   `category = 'student'`) — this is exactly why those two, unlike
   goals/recognition/recruitment, were folded into `manage-my-people`
   itself rather than a separate wrapper (see
   [doc 27 §5](../architecture/27-people-domain-design.md#5-attendance--leave-category-agnostic-not-employee-only)).

No wrapper app should ever need to modify a file under
`apps/manage-my-people/`.
