# Requirement: Manage My Data

**App:** `manage-my-data` (new, `apps/manage-my-data`)
**DB target:** `opzdata` (per-company, own logical database — [doc 28](../architecture/28-per-application-database-design.md))
**Access level:** Role-based per feature (see RBAC in DB section) — no single fixed role
**Architecture ref:** [doc 29](../architecture/29-data-directory-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md) · [doc 26 §2](../architecture/26-rbac-db-design.md) · [doc 22](../architecture/22-common-fields-forms-fk.md)
**Reference source:** `refer_mmo/opz-data` (requirements extracted only — no code, package, or table name reused)

---

## What It Does

A business/lead data directory: business records move through a
structured intake → assignment → contact → detail-collection →
verification → approval → publish workflow, with company-managed
category and location taxonomies, an audit trail of every stage
transition, and — once published — public inquiries and reviews. Matches
the app catalog's own description: "Centralize and manage your business
data." See [doc 29 §1](../architecture/29-data-directory-domain-design.md#1-what-the-reference-app-actually-is)
for the evidence behind this scope (the reference persists 4 real
entities plus 2 more specified-but-unwired in DDL; this design completes
the two unwired ones rather than dropping them, since a directory with no
inquiry/review path is not a credible requirement) and
[doc 29 §2](../architecture/29-data-directory-domain-design.md#2-domain-model)
for the fixes applied over the reference (multi-tenancy added; hardcoded
India-only geography and a hardcoded category list replaced with
company-managed taxonomies; unmoderated public reviews given a status
gate).

This app owns no identity or RBAC data of its own — every person
reference (assigned marketing manager, assigned call agent, listing
owner, inquirer, reviewer) is a cross-database logical link to
`modules/identity`'s `id_user`. The reference app's local `User`/
`StaffUser` tables and flat `role` string column are explicitly **not**
reproduced — see Dependencies and
[doc 29 §2.4](../architecture/29-data-directory-domain-design.md#24-no-local-identity-no-local-role-string).

**Design-only drop:** per instruction, this requirement and its
architecture doc are design artifacts. The **only** files actually created
in this drop are the DB schema YAML files under
[`apps/manage-my-data/db/schema/`](../../../apps/manage-my-data/db/schema/).
Everything else in "Files to Create" below is implementation for later.

---

## Entities & Tables

All tables are in `opzdata` — this app's **own** logical database, per
[doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application),
not the shared `opzmain`. `company_id` and every `*_user_id` column are
therefore **cross-database** (to `opzmain` and `opzuser`), enforced at
the application layer per
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
never a Postgres `FOREIGN KEY`. Full column definitions, indexes, and
foreign keys are the schema YAML files under
`apps/manage-my-data/db/schema/` — this section is the human-readable
summary.

### `dat_business` — core business/lead record

| Column (group) | Notes |
|---|---|
| `id`, `company_id`, `record_code` | Identity; `record_code` generated via `dat_record_code_sequence`, replacing the reference's timestamp-based id |
| `business_name`, `category_id`, `business_type` | What it is (`SERVICE_PROVIDER` \| `MANUFACTURER_SUPPLIER`) |
| `phone`, `email`, `website`, `address_line`, `location_id` | Contact + geography, normalized into `dat_location` instead of hardcoded state/city/area defaults |
| `description`, `rating_average`, `reviews_count` | Public-facing profile; the last two are **maintained aggregates**, written only by the review service |
| `stage` | 9-value canonical pipeline plus `UNPUBLISHED`/`ARCHIVED` exits (see Business Rules) |
| `assigned_marketing_manager_user_id`, `assigned_call_agent_user_id`, `owner_user_id`, `created_by_user_id` | All optional cross-database links to `id_user` |

### `dat_category` / `dat_location`

Both hierarchical (self-referencing `parent_category_id` /
`parent_location_id`), company-scoped catalogs — company-managed data,
not hardcoded frontend lists. `dat_location.location_type`: `COUNTRY` \|
`STATE` \| `CITY` \| `AREA` \| `CUSTOM`.

### `dat_business_workflow_log`

Append-only stage-transition audit trail: `business_id`, `from_stage`,
`to_stage`, `author_user_id`/`author_name` (fallback for system entries),
`comment`, `created_at`.

### `dat_business_inquiry`

`business_id`, `inquirer_user_id` (optional — anonymous inquiries
allowed), `inquirer_name`, `inquirer_email`, `message`, `status`
(`NEW`\|`RESPONDED`\|`CLOSED`), `created_at`.

### `dat_business_review`

`business_id`, `reviewer_user_id` (optional), `reviewer_name`, `rating`
(1-5), `comment`, `status` (`PENDING`\|`PUBLISHED`\|`REJECTED`),
`created_at`. Only `PUBLISHED` rows count toward
`dat_business.rating_average`/`reviews_count`.

### `dat_business_custom_field`

EAV value store (`business_id`, `field_key`, `field_value`,
`field_group`, `display_order`). Definitions in `id_field_definition`
(`form_id = "manage-my-data.business.custom"`) — same pattern as
`apps/manage-my-people`'s `ppl_person_custom_field`.

### `dat_record_code_sequence`

`company_id`, `prefix`, `next_number` — generates `dat_business.record_code`.

---

## API Endpoints

Base path: `/api/v1/opzhub/manage-my-data`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/businesses` | Create a business record (`stage` forced to `LEAD_CREATED` server-side) |
| `POST` | `/businesses/read` | List records (paginated, filterable by `stage`, `category_id`, `location_id`, `business_type`) or read by `id` |
| `PUT` | `/businesses` | Update record details (excludes `rating_average`/`reviews_count`/`stage` — see dedicated endpoints) |
| `DELETE` | `/businesses` | Archive a record (`stage → ARCHIVED`) — not a hard delete while logs/inquiries/reviews exist |
| `GET` | `/businesses/code/preview` | Preview next `record_code` for a prefix |
| `POST` | `/businesses/code/reserve` | Reserve and return the next `record_code` |
| `PUT` | `/businesses/{id}/stage` | Transition stage (role-gated by target stage — see RBAC); writes a `dat_business_workflow_log` row |
| `POST` | `/businesses/{id}/logs` / `.../read` | Add / list workflow log entries (manual comments and system transitions) |
| `POST` | `/categories` / `.../read` / `PUT` / `DELETE` | Category CRUD (admin) |
| `POST` | `/locations` / `.../read` / `PUT` / `DELETE` | Location CRUD (admin) |
| `POST` | `/businesses/{id}/inquiries` (public) / `.../read` / `PUT /inquiries` | Inquiry submit (public) / list (staff) / status update (staff) |
| `POST` | `/businesses/{id}/reviews` (public) / `.../read` / `PUT /reviews` | Review submit (public, `status = PENDING`) / list / moderate (staff) |
| `POST` | `/businesses/{id}/custom-fields` / `.../read` | Custom field values (definitions from `GET /forms/manage-my-data.business.custom`) |
| `GET` | `/directory` (public) | Published-only, public-facing browse/search endpoint — never exposes unpublished stages or internal assignment fields |

All authenticated responses use the standard `ApiEnvelope<T>` wrapper
with `correlation_id`. The public `/directory` and inquiry/review submit
endpoints are rate-limited and CAPTCHA-gated at the gateway layer, same
as any other unauthenticated write surface.

---

## SQL Commands

Named SQL files in `apps/manage-my-data/db/commands/`:

```
business.insert.sql
business.find_by_id.sql
business.find_by_company_and_code.sql
business.list_paged.sql
business.update_details.sql          -- excludes rating_average, reviews_count, stage
business.update_stage.sql
business.archive.sql
record_code_sequence.reserve_next.sql -- single UPDATE ... RETURNING, no race
workflow_log.insert.sql
workflow_log.list_by_business.sql
category.list_by_company.sql
location.list_by_company.sql
inquiry.insert.sql / .list_by_business.sql / .update_status.sql
review.insert.sql / .list_by_business.sql / .moderate.sql
business.recompute_rating.sql         -- AVG/COUNT over PUBLISHED reviews for one business_id
business_custom_field.upsert.sql
```

`record_code_sequence.reserve_next.sql` must be a single atomic
`UPDATE ... RETURNING` (`ON CONFLICT` insert-on-missing) — never a
`SELECT` then `UPDATE`, same requirement as the other two apps' code
sequences.

---

## Files to Create

```
apps/manage-my-data/
├── module.yaml                                              ← NEW (design only, not created this drop)
├── backend/src/main/java/com/managemyopz/apps/managemydata/
│   ├── ManageMyDataAutoConfiguration.java                   ← NEW
│   ├── api/
│   │   ├── BusinessController.java
│   │   ├── BusinessStageController.java
│   │   ├── WorkflowLogController.java
│   │   ├── CategoryController.java
│   │   ├── LocationController.java
│   │   ├── InquiryController.java
│   │   ├── ReviewController.java
│   │   ├── PublicDirectoryController.java                   (unauthenticated read + submit endpoints)
│   │   └── dto/                                              (Create/Update/Response DTOs per entity)
│   ├── application/
│   │   ├── BusinessService.java
│   │   ├── BusinessStageService.java                         (stage-transition validation + role gate)
│   │   ├── RecordCodeSequenceService.java
│   │   ├── WorkflowLogService.java
│   │   ├── CategoryService.java
│   │   ├── LocationService.java
│   │   ├── InquiryService.java
│   │   ├── ReviewService.java                                (moderation + rating_average/reviews_count maintenance)
│   │   └── ManageMyDataConstants.java
│   ├── domain/
│   │   ├── Business.java
│   │   ├── Category.java
│   │   ├── Location.java
│   │   └── ... (one record per entity)
│   └── data/
│       ├── BusinessRepository.java / DataClientBusinessRepository.java
│       └── ... (one repository pair per entity)
├── db/
│   ├── schema/                                               ← EXISTS (this drop) — 8 files, database: OPZDATA
│   │   ├── dat_business.yaml
│   │   ├── dat_category.yaml
│   │   ├── dat_location.yaml
│   │   ├── dat_business_workflow_log.yaml
│   │   ├── dat_business_inquiry.yaml
│   │   ├── dat_business_review.yaml
│   │   ├── dat_business_custom_field.yaml
│   │   └── dat_record_code_sequence.yaml
│   └── commands/                                             ← NEW (see SQL Commands above)
├── forms/                                                    ← NEW (catalog defaults, doc 22 §7)
│   ├── business.create.yaml
│   ├── business.edit.yaml
│   ├── business.list.yaml
│   ├── business-custom.yaml
│   ├── category.edit.yaml
│   ├── location.edit.yaml
│   ├── inquiry.public.yaml
│   └── review.public.yaml
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   ├── menu.ts
│   ├── manageMyDataConstants.ts
│   └── pages/
│       ├── BusinessListPage.tsx
│       ├── BusinessDetailPage.tsx                            (tabs: details, workflow log, inquiries, reviews)
│       ├── CategoryAdminPage.tsx
│       ├── LocationAdminPage.tsx
│       ├── PublicDirectoryPage.tsx                           (public browse/search)
│       └── PublicBusinessProfilePage.tsx                     (public listing page with inquiry/review submit)
└── mobile/
    ├── plugin.dart
    └── pages/
        ├── business_list_page.dart
        └── business_detail_page.dart
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| `record_code` unique per company, generated (not user-typed) | `record_code_sequence.reserve_next.sql`; DB unique index as final guard |
| Every new record starts at `stage = LEAD_CREATED` regardless of client input | `BusinessService.create()` forces it server-side |
| Stage transitions follow the canonical order and are role-gated per target stage | `BusinessStageService` — see RBAC in DB |
| Every stage transition writes a `dat_business_workflow_log` row | `PUT /businesses/{id}/stage` always calls `WorkflowLogService` in the same transaction |
| Archiving a record is always `stage = ARCHIVED`, never a physical `DELETE` | `BusinessService.archive()` — the `DELETE /businesses` endpoint calls this |
| `rating_average`/`reviews_count` are never client-writable | Excluded from `BusinessUpdateRequest`; only `ReviewService.moderate()` (on `status → PUBLISHED`) recomputes them via `business.recompute_rating.sql` |
| A review only counts toward the business's rating once `status = PUBLISHED` | `ReviewService` — `PENDING`/`REJECTED` rows excluded from the aggregate query |
| The public `/directory` and public profile endpoints never return `assigned_marketing_manager_user_id`, `assigned_call_agent_user_id`, `owner_user_id`, or any non-`PUBLISHED`-stage record | `PublicDirectoryController` uses a dedicated, narrower response DTO — never the internal `BusinessResponse` |
| Every table's `company_id` and every `*_user_id` are validated against the owning database before write, never assumed present | Service-layer existence check per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication), same `Conflict`/`err: fk` shape as a same-database FK |

---

## Dependencies

- `identity` module — session, RBAC tables (`id_role`, `id_user_role`,
  `id_role_permission`, `id_user_permission`, `id_field_definition` from
  [doc 26](../architecture/26-rbac-db-design.md)), and a registered named
  command this app calls cross-database (`identity.find_user_by_id`,
  `opzuser`) to resolve staff display names. **No schema change to
  `identity` is required.**
- `company-setup` — `company_information` (`opzmain`) must exist for the
  cross-database `company_id` reference on every table in this app.
- **New platform capability:** `OPZDATA` as a provisioned logical
  database (connection pool, migration target, backup schedule) —
  [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).
- `modules/apps` — this app already has a catalog entry
  (`app_key: manage-my-data`, `opz-001`, category `data`) in
  `modules/apps/db/seed/application_catalog.yaml`; no seed change is
  needed. `ApplicationFolderPresence` still requires
  `apps/manage-my-data/{frontend,backend,mobile}` to exist before the app
  can be licensed — not performed in this drop (implementation only).
- Public, unauthenticated endpoints (`/directory`, inquiry/review submit)
  depend on gateway-level rate limiting and CAPTCHA — an infra/Nginx
  concern ([09](../architecture/09-docker-nginx-ec2.md)), not this app's
  schema, but called out because those endpoints don't function safely
  without it.

---

## GUI Metadata Design

Every screen renders from `id_field_definition` via `GET
/api/v1/opzhub/forms/{form_id}` ([doc 22](../architecture/22-common-fields-forms-fk.md) §3).

### Screen: Business Record (`manage-my-data.business.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `business_name` | Business Name | `text` | Yes | Max 200 chars | `biz` `c`/`u` | — |
| `category_id` | Category | `lookup` | No | `fk: { res: "manage-my-data.category" }` | `biz` `c`/`u` | — |
| `business_type` | Type | `radio` | Yes | `SERVICE_PROVIDER`, `MANUFACTURER_SUPPLIER` | `biz` `c`/`u` | — |
| `location_id` | Location | `lookup` | No | `fk: { res: "manage-my-data.location" }`, cascading COUNTRY→STATE→CITY→AREA picker | `biz` `c`/`u` | — |
| `stage` | Stage | `select` | Yes | 9-value pipeline + `UNPUBLISHED`/`ARCHIVED` | `wfl` per target stage (see RBAC) | Not editable on this form — driven by the dedicated stage-transition action, `mode: view` here |
| `owner_user_id` | Listing Owner | `lookup` | No | `fk: { res: "identity.user" }` | `biz` `u`, `role_visibility: {"business_owner":"hidden"}` | Business owners cannot reassign their own listing's ownership |

### Screen: Public Business Profile (`manage-my-data.business.public-profile`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `rating_average` | Rating | `display` | — | Read-only, computed | Public | — |
| `reviewer_name` (on submit review) | Your Name | `text` | Yes | Max 100 chars | Public / `individual_buyer` | — |
| `rating` (on submit review) | Your Rating | `radio` | Yes | 1–5 | Public / `individual_buyer` | Review enters `PENDING` — "awaiting moderation" hint shown after submit |
| `message` (on submit inquiry) | Your Message | `textarea` | Yes | Max 2000 chars, `deny: ["html"]` | Public / `individual_buyer` | — |

### Screen: Business Directory (list, staff)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `record_code` | Code | `text` | Yes | `biz` `v` |
| `business_name` | Business | `text` | Yes | `biz` `v` |
| `category_id` (joined) | Category | `text` | No | `biz` `v` |
| `stage` | Stage | `status-badge` | Yes | `biz` `v` |
| `rating_average` | Rating | `text` | Yes | `biz` `v` |
| — | Actions | `actions` | No | `biz` `v` | View, Edit (`u`), Archive (`d`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import:
[csv]` with `ops: [c]` (bulk lead import is a real, common directory
workflow, unlike ticket/person creation which stayed code-reservation-
only in the other two apps — each new imported row still reserves its
own `record_code` server-side during the import commit, so uniqueness is
never bypassed), `bulk: { u: true, d: false }`.

### Metadata-Driven Rules

- `stage` is never a free-editable field on the business form — the
  detail page always uses a dedicated "Advance Stage" action bound to
  `PUT /businesses/{id}/stage`, which only offers the *next valid* stage
  (and role-permitted target stages) from the FormEnvelope's `opts` —
  never every stage value.
- `location_id` renders as a cascading picker (country → state → city →
  area) built from `dat_location`'s hierarchy via the kernel `lookup`
  control with a parent-scoped query — not four independent dropdowns.
- Public review/inquiry submission forms never show internal fields
  (`assigned_*_user_id`, `stage`, `owner_user_id`) — the public FormEnvelope
  (`manage-my-data.business.public-profile`) is a **separate, narrower**
  form registration, not the internal edit form with fields hidden by CSS.

---

## Directory Placement

```
apps/manage-my-data/            ← Application-specific (sold, licensed app)
│                                   Own logical database: OPZDATA (doc 28 §2)
├── backend/
├── frontend/
│   └── pages/                  ← Uses field primitives from common/frontend, not its own copies
├── mobile/
├── forms/                      ← Catalog defaults; runtime = id_field_definition (doc 26) + FormEnvelope (doc 22)
└── db/
    ├── schema/                 ← EXISTS (this drop) — 8 files, database: OPZDATA
    └── commands/

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reused for category_id, location_id (cascading), owner_user_id
│   └── ... (no new control types needed — this app introduces no field kind not already in doc 22 §2)
├── theme/tokens.ts               ← Reused; no new colors
└── icons/                        ← One new icon component: "data" (matches this app's icon_key)
```

No changes to `modules/identity/` or `modules/apps/` are required — see
Dependencies above.

**Rules:**
- No module-local reimplementation of `LookupField`, `DataTable`, or any
  field control ([doc 22](../architecture/22-common-fields-forms-fk.md) §10).
- `dat_category` / `dat_location` lookups are reused by every screen
  (internal and public) in this app via the kernel `LookupField` +
  `/lookup/{res}` API — never a per-screen hardcoded option list.

---

## Constants

### Backend (`apps/manage-my-data/backend/.../ManageMyDataConstants.java`)

```java
public static final String BUSINESS_TYPE_SERVICE_PROVIDER   = "SERVICE_PROVIDER";
public static final String BUSINESS_TYPE_MANUFACTURER       = "MANUFACTURER_SUPPLIER";

public static final String STAGE_LEAD_CREATED               = "LEAD_CREATED";
public static final String STAGE_ASSIGNED_MM                = "ASSIGNED_MARKETING_MANAGER";
public static final String STAGE_ASSIGNED_CALL_AGENT        = "ASSIGNED_CALL_USER";
public static final String STAGE_BUSINESS_CONTACTED         = "BUSINESS_CONTACTED";
public static final String STAGE_DETAILS_COLLECTED          = "DETAILS_COLLECTED";
public static final String STAGE_PROFILE_CREATED            = "PROFILE_CREATED";
public static final String STAGE_VERIFIED_MANAGER           = "VERIFIED_MANAGER";
public static final String STAGE_APPROVED_ADMIN             = "APPROVED_ADMIN";
public static final String STAGE_PUBLISHED                  = "PUBLISHED";
public static final String STAGE_UNPUBLISHED                = "UNPUBLISHED";
public static final String STAGE_ARCHIVED                   = "ARCHIVED";

public static final String REVIEW_STATUS_PENDING            = "PENDING";
public static final String REVIEW_STATUS_PUBLISHED          = "PUBLISHED";
public static final String REVIEW_STATUS_REJECTED           = "REJECTED";

public static final int    RECORD_CODE_MAX_LEN              = 32;
```

### Frontend (`apps/manage-my-data/frontend/manageMyDataConstants.ts`)

```typescript
export const DATA_API_BASE             = "/api/v1/opzhub/manage-my-data";
export const BUSINESS_LIST_HEADING     = "Business Directory";
export const BUSINESS_TYPE_OPTIONS = [
  { value: "SERVICE_PROVIDER",     label: "Service Provider"      },
  { value: "MANUFACTURER_SUPPLIER", label: "Manufacturer & Supplier" },
];
export const STAGE_LABELS: Record<string, string> = {
  LEAD_CREATED:               "Lead Created",
  ASSIGNED_MARKETING_MANAGER: "Assigned to Marketing Manager",
  ASSIGNED_CALL_USER:         "Assigned to Call Agent",
  BUSINESS_CONTACTED:         "Business Contacted",
  DETAILS_COLLECTED:          "Details Collected",
  PROFILE_CREATED:            "Profile Created",
  VERIFIED_MANAGER:           "Verified by Manager",
  APPROVED_ADMIN:             "Approved",
  PUBLISHED:                  "Published",
  UNPUBLISHED:                "Unpublished",
  ARCHIVED:                   "Archived",
};
```

Colors, spacing, and icon keys stay in `common/frontend/src/theme/tokens.ts`
and `common/frontend/src/icons/` — not duplicated here
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).
The reference app's hardcoded `CATEGORIES` array and Delhi/Connaught-Place
location defaults are **not** ported into any constants file — both are
`dat_category`/`dat_location` data (§2.1 of the architecture doc).

---

## Optimization, Performance & Memory

See [doc 29 §5](../architecture/29-data-directory-domain-design.md#5-performance-optimization-memory-domain-specific-notes)
for the domain-specific reasoning. Summary of concrete rules:

### Performance
- `dat_category`/`dat_location` cached per company in `CacheClient`
  (explicit TTL, evicted on write).
- Business directory list (staff and public) is always server-paginated
  with filters pushed into `business.list_paged.sql`.
- `record_code_sequence.reserve_next.sql` is one atomic
  `UPDATE ... RETURNING` — no read-then-write race, including during
  bulk import.
- `rating_average`/`reviews_count` are maintained columns — the public
  directory list never runs a per-row `AVG`/`COUNT` over reviews.

### Memory
- **React:** `BusinessDetailPage` tabs (workflow log, inquiries, reviews)
  fetch their own data lazily on tab-select — same pattern as
  `apps/manage-my-people`'s `PersonProfilePage`.
- **Java:** `WorkflowLogService` streams log pages
  (`workflow_log.list_by_business.sql`) — never loads a record's full
  history into one list.
- Public directory search results are paginated server-side with a hard
  page-size cap — an unauthenticated endpoint must not allow an
  unbounded result set to be requested.

### Optimization
- Bulk import (§ GUI Metadata Design) validates and reserves
  `record_code` per row inside one server-side commit — never a client-
  side loop of individual `POST /businesses` calls.
- Bulk update on the directory touches only `mode: edit` fields per doc
  22 §10.2 — never a blind full-row rewrite.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> Data directory domain design: [doc 29](../architecture/29-data-directory-domain-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-data/` (mirroring
the `01-unit/modules/<name>/` convention from
[Rule 1](IMPLEMENTATION_RULES.md#rule-1--unit-test-cases-separate-repo),
extended for the `apps/` vs `modules/` split, same as the other two
`apps/manage-my-*` requirement docs). No test files under
`apps/manage-my-data/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `BusinessServiceTest` | Create forces `LEAD_CREATED`; archive-not-delete; `record_code` reservation atomicity |
| `BusinessStageServiceTest` | Only valid next-stage transitions allowed; role gate per target stage; every transition writes a log row |
| `ReviewServiceTest` | Only `PUBLISHED` reviews count toward `rating_average`/`reviews_count`; moderation transition recomputes correctly |
| `PublicDirectoryControllerTest` | Never returns non-`PUBLISHED` records or internal assignment fields |
| `BusinessCustomFieldServiceTest` | Write rejected for a `field_key` not present in `id_field_definition` |

### RBAC in DB

Feature ids: `biz`, `wfl`, `inq`, `rev`, `cat`, `loc` (full table and
rationale: [doc 29 §4](../architecture/29-data-directory-domain-design.md#4-rbac--abac)).

Starter roles seeded by this app's migration — six roles, mapped from
the reference's seven (its two admin tiers collapse into one
`data_admin`):

```sql
INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('data_admin',          'Data Administrator',    true),
  ('data_entry_user',     'Data Entry User',        true),
  ('data_marketing_mgr',  'Marketing Manager',      true),
  ('data_call_agent',     'Call Agent',             true),
  ('data_business_owner', 'Business Listing Owner', true),
  ('data_buyer',          'Individual Buyer',       true);

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('data_admin',          'manage-my-data', 'biz', 'vcud'),
  ('data_admin',          'manage-my-data', 'wfl', 'vcu'),
  ('data_admin',          'manage-my-data', 'inq', 'vu'),
  ('data_admin',          'manage-my-data', 'rev', 'vu'),
  ('data_admin',          'manage-my-data', 'cat', 'vcud'),
  ('data_admin',          'manage-my-data', 'loc', 'vcud'),
  ('data_entry_user',     'manage-my-data', 'biz', 'vc'),
  ('data_entry_user',     'manage-my-data', 'wfl', 'v'),
  ('data_marketing_mgr',  'manage-my-data', 'biz', 'vu'),
  ('data_marketing_mgr',  'manage-my-data', 'wfl', 'vcu'),
  ('data_marketing_mgr',  'manage-my-data', 'inq', 'v'),
  ('data_call_agent',     'manage-my-data', 'biz', 'vu'),
  ('data_call_agent',     'manage-my-data', 'wfl', 'vc'),
  ('data_business_owner', 'manage-my-data', 'biz', 'vu'),
  ('data_buyer',          'manage-my-data', 'inq', 'c'),
  ('data_buyer',          'manage-my-data', 'rev', 'c');
```

`data_marketing_mgr`'s `wfl.u` is the only role (besides `data_admin`)
permitted to transition a record to `VERIFIED_MANAGER`; only `data_admin`
may transition to `APPROVED_ADMIN`/`PUBLISHED` — enforced in
`BusinessStageService`, not by the RBAC letter alone (§4 of the
architecture doc). `data_call_agent`'s `biz.u` and `data_business_owner`'s
`biz.u` are further narrowed by ABAC:
`resource.assigned_call_agent_user_id == user.id` /
`resource.owner_user_id == user.id` respectively. Company admins may
layer `id_user_permission` GRANT/REVOKE rows on top of these six starter
roles without creating new roles, per
[Rule 2](IMPLEMENTATION_RULES.md#rule-2--rbac-in-db-with-optimized-tables).

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `manage-my-data.business.create` | Create Business Record |
| `manage-my-data.business.edit` | Edit Business Record |
| `manage-my-data.business.list` | Business Directory (staff) |
| `manage-my-data.business.custom` | Custom Fields (per-company defined) |
| `manage-my-data.business.public-profile` | Public Listing Page (view + inquiry/review submit) |
| `manage-my-data.category.edit` | Category Admin |
| `manage-my-data.location.edit` | Location Admin |

Example migration row (`stage` field, DB-driven allowed values,
`view`-only on the general edit form):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, display_order, allowed_values, role_visibility
) VALUES (
  'manage-my-data.business.edit', 'stage', 'Stage', 'select',
  true, 5,
  '[{"value":"LEAD_CREATED","label":"Lead Created"},
    {"value":"ASSIGNED_MARKETING_MANAGER","label":"Assigned to Marketing Manager"},
    {"value":"ASSIGNED_CALL_USER","label":"Assigned to Call Agent"},
    {"value":"BUSINESS_CONTACTED","label":"Business Contacted"},
    {"value":"DETAILS_COLLECTED","label":"Details Collected"},
    {"value":"PROFILE_CREATED","label":"Profile Created"},
    {"value":"VERIFIED_MANAGER","label":"Verified by Manager"},
    {"value":"APPROVED_ADMIN","label":"Approved"},
    {"value":"PUBLISHED","label":"Published"},
    {"value":"UNPUBLISHED","label":"Unpublished"},
    {"value":"ARCHIVED","label":"Archived"}]'::jsonb,
  '{"default":"readonly"}'::jsonb
);
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /businesses` | `SessionAuthFilter` (401) | `biz.c` | Tenant-scoped only |
| `POST /businesses/read` | 401 | `biz.v` | Non-admins see rows where they are `assigned_call_agent_user_id`, `assigned_marketing_manager_user_id`, or `owner_user_id`; admins unscoped |
| `PUT /businesses/{id}/stage` | 401 | `wfl.u` (or `wfl.c` for `data_call_agent`) | Target-stage role gate (§4); `data_business_owner`/`data_buyer` never hold `wfl.*` |
| `DELETE /businesses` | 401 | `biz.d` | `data_admin` only in practice |
| `POST /businesses/{id}/reviews` | None (public) or 401 (logged-in buyer) | `rev.c` for authenticated buyers; public path is a separate, unauthenticated, rate-limited endpoint | New rows always `status = PENDING` regardless of caller |
| `GET /directory` | None (public) | — | Query is hardcoded to `stage = 'PUBLISHED'`; no caller input can widen it |

Every controller method carries `@RequiresPermission(module =
"manage-my-data", feature = <id>, action = <letter>)`
([Rule 4](IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level)).
Public endpoints (`/directory`, inquiry/review submit) are the explicit
exception — they run behind `security.allow_open_dev`-style unauthenticated
routing already used elsewhere for public/self-service surfaces, never a
`@RequiresPermission` bypass hack.

### Coding Standards (this feature)

**Java:** Domain entities (`Business`, `Category`, `Location`, ...) are
immutable `record`s. All SQL in `apps/manage-my-data/db/commands/`. No
business logic in controllers — delegate to `*Service`. Constants in
`ManageMyDataConstants.java` only.

**Flutter:** `ManageMyDataConstants` class in
`apps/manage-my-data/mobile/lib/constants/`. All `TextEditingController`
instances disposed in `dispose()`.

**TypeScript/React:** `BUSINESS_TYPE_OPTIONS`, `STAGE_LABELS`, and
`DATA_API_BASE` in `manageMyDataConstants.ts`. `LookupField` and
`DataTable` imported from `common/frontend` — not re-implemented.

### Directory Confirmation

```
apps/manage-my-data/
    backend/          ← all controller + service + repository Java
    frontend/          ← pages using common field primitives
    mobile/            ← Flutter pages using common widgets
    forms/             ← catalog defaults compiled into FormEnvelope
    db/schema/         ← 8 schema YAML files (this drop), database: OPZDATA
    db/commands/       ← all SQL named commands (implementation)
common/frontend/src/
    icons/             ← + one new "data" icon component
    theme/tokens.ts    ← reused, unchanged
```
