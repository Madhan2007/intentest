# 29 — Data Directory Domain Design (`apps/manage-my-data`)

This document is **design only** — see [design/docs/requirement/manage-my-data.md](../requirement/manage-my-data.md)
for the implementation-ready requirement, and [apps/manage-my-data/db/schema/](../../../apps/manage-my-data/db/schema/)
for the DB schema YAML files (the one exception to "design only" in this drop).
This app's tables live in their own logical database, `OPZDATA`, per
[doc 28](28-per-application-database-design.md) — every `company_id →
company_information` reference, and every `*_user_id → id_user`
reference, below is **cross-database**, enforced at the application
layer, never a Postgres `FOREIGN KEY`.

Reference source: `refer_mmo/opz-data` (an existing, not-cleanly-structured
"Yellow Pages"-style business directory prototype) was read to extract
*requirements*, not copied. No code, package name, or table name from
that app is reused as-is.

## 1. What the reference app actually is

`opz-data` is a business/lead directory and intake-to-publish workflow
tool: a company centralizes external business listings, runs them through
a structured 9-stage pipeline (lead capture → assignment → contact →
detail collection → profile creation → manager verification → admin
approval → publish), and — once published — the public can browse
listings, submit inquiries, and leave reviews. This matches the app
catalog's own description ("Centralize and manage your business data").

The reference's Java backend persists only **4 real entities** —
`Business`, `StaffUser`, `User`, `WorkflowLog` — with matching
controller/repository code. `schema.sql` additionally defines
`inquiries` and `reviews` tables, but **no Java entity, repository, or
controller exists for either** — they are specified in DDL but never
wired up. Unlike `apps/manage-my-desk`'s reference (where excluded pages
had *no* backend specification at all — pure frontend mockup), inquiries
and reviews have a genuine, concrete data shape already designed in SQL;
a business directory without a public inquiry/review path is also not a
credible requirement. This design therefore **completes** them
(`dat_business_inquiry`, `dat_business_review`) rather than excluding
them, with the moderation/status gates their unwired reference version
was missing (§2.3).

The reference itself has three problems this design fixes rather than
replicates:

1. **No multi-tenancy.** `businesses`, `staff_users`, `users` are single
   global tables with no `company_id` anywhere.
2. **A duplicated authentication system.** `User` and `StaffUser` are a
   parallel identity model — `users.role` even encodes both *internal*
   roles (`data_entry_user`, `marketing_manager`, `staff`) and *external*
   self-service roles (`business_owner`, `individual_buyer`) in one flat
   string column with no RBAC table backing it. Same anti-pattern
   [doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)
   and `apps/manage-my-desk`'s design doc already document.
3. **Hardcoded, India-only geography and a hardcoded category list.**
   `Business.onCreate()` defaults `state`/`city`/`area` to
   `"Delhi"`/`"Connaught Place"` when unset, and the frontend's
   `CATEGORIES` array is a hardcoded 28-entry list — neither is
   company-configurable or usable outside India without a code change.

## 2. Domain model

Full column-level detail is in the requirement doc and the schema YAML
files. This section covers the generalization and correctness decisions
that differ from the reference.

### 2.1 `dat_location` and `dat_category` — taxonomy as data, not code

Both replace hardcoded frontend arrays / defaulted flat columns with a
self-referencing hierarchy table (`location_type`/`parent_location_id`,
`parent_category_id`) — the same generalize-via-discriminator technique
used for `ppl_org_unit` (`apps/manage-my-people`) and `dsk_category`
(`apps/manage-my-desk`). A directory app's core UX — "browse by state >
city > area", "browse by category" — needs a real hierarchy for drill-down
navigation, not just a flat filter, so this is a functional requirement,
not only a hardcoding fix. Because it is company-scoped data, a company
outside India (or one that doesn't use state/city/area at all — a country
using postal-district instead of state, say) configures its own taxonomy;
none of it is compiled into the frontend.

### 2.2 `dat_business` — one record, real workflow columns

`stage` keeps the reference's 9-value canonical pipeline
(`STAGE_FLOW` in its frontend `mockData.js`) intact, plus two additions
the reference never modeled: `UNPUBLISHED` and `ARCHIVED`, so a published
listing can be taken down — a live directory needs an exit path, not only
an entry pipeline. `reviews_count`/`rating_average` are **maintained
aggregates**, recomputed by the review service in the same transaction as
a published review write, not the reference's counter that any direct
`PUT /businesses/{id}` could silently desync (the reference's `update`
endpoint lets a caller overwrite every field including `rating`/
`reviewsCount` directly — this design keeps them off the general update
DTO and only written by `BusinessReviewService`).

### 2.3 Public-facing tables get a moderation/status gate

The reference's `inquiries` and `reviews` DDL (unwired, §1) has no status
column at all — every review would auto-publish. `dat_business_review`
adds `status` (`PENDING`\|`PUBLISHED`\|`REJECTED`, default `PENDING`) and
`dat_business_inquiry` adds `status` (`NEW`\|`RESPONDED`\|`CLOSED`) —
unmoderated public review text is a real spam/abuse surface, and an
inquiry with no lifecycle state is unactionable by staff.

### 2.4 No local identity, no local role string

Every "who" column (`assigned_marketing_manager_user_id`,
`assigned_call_agent_user_id`, `owner_user_id`, `created_by_user_id`,
`author_user_id`, `inquirer_user_id`, `reviewer_user_id`) is a
**cross-database logical reference to `id_user.id` on `OPZUSER`**
([doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)) —
never a local `dat_user`/`dat_staff` copy, and never a flat `role` string
column. The reference's mixed internal/external role list (`data_admin`
through `individual_buyer`) becomes RBAC role seed rows against the
platform's own `id_role`/`id_role_permission` (§4), the same way every
other app in this catalog maps its roles.

`owner_user_id` is a genuinely new column, not just a rename: the
reference's `ROLES.BUSINESS_OWNER` exists in its role model but is never
actually linked to a `businesses` row anywhere in the schema — self-
service owners had a login but no way to be tied to *their* listing. This
design adds the column so that relationship is real.

## 3. GUI metadata — confirmed mechanism, no new table

Same mechanism as `apps/manage-my-people` and `apps/manage-my-desk`:
every field-level requirement is served by `id_field_definition`
([26](26-rbac-db-design.md) §2) through `FormEnvelope`
([22](22-common-fields-forms-fk.md) §3). `dat_business_custom_field`
mirrors `ppl_person_custom_field` / `dsk_ticket_custom_field`'s EAV
pattern exactly: definitions in `id_field_definition` under
`form_id = "manage-my-data.business.custom"`, values in
`dat_business_custom_field`.

## 4. RBAC / ABAC

Feature ids declared for `module_id = "manage-my-data"` (≤4 chars,
[18](18-identity-rbac-abac-oauth2.md) §3.1, cap 32/app):

| Feature id | Title | Covers |
|---|---|---|
| `biz` | Business records | `dat_business`, `dat_business_custom_field` |
| `wfl` | Workflow | `dat_business_workflow_log`, stage transitions |
| `inq` | Inquiries | `dat_business_inquiry` |
| `rev` | Reviews | `dat_business_review` |
| `cat` | Categories | `dat_category` |
| `loc` | Locations | `dat_location` |

Enforcement is the standard two-layer stack from
[Rule 4](../requirement/IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level).
Stage transitions are additionally **role-gated by target stage**, not
just by the generic `wfl` feature letter — e.g. only a role holding the
`marketing_manager`-equivalent grant may move a record to
`VERIFIED_MANAGER`; only `data_admin` may move it to `APPROVED_ADMIN` or
`PUBLISHED`. This mirrors the reference's intent (only "Super Admin"
publishes) but enforces it server-side per transition rather than trusting
the client to only show valid next-stage buttons. Full role list and
seed rows are in the requirement doc's "RBAC in DB" section — six roles,
directly mapped from the reference's seven (its two admin tiers,
`ultra_super_admin`/`super_admin`, collapse into one `data_admin` here;
that two-tier distinction is a platform-level concern, not something this
app's RBAC should re-litigate).

## 5. Performance, optimization, memory (domain-specific notes)

Beyond the universal rules in
[Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal):

- `dat_category` and `dat_location` are read-heavy, write-rare catalogs
  used to populate every business form's lookups and the public
  directory's browse/filter UI — cached in `CacheClient` per company with
  explicit TTL, evicted on write, same pattern as
  `ppl_org_unit`/`dsk_category`.
- `dat_business_workflow_log` is an unbounded-growth append-only child
  table. Directory/list screens never join it; it loads only on a single
  record's detail view, paginated.
- `rating_average`/`reviews_count` being maintained columns (§2.2) means
  the public directory's list/search page never runs a
  `COUNT`/`AVG` aggregate over reviews per row rendered — a real
  N+1-aggregate avoided by design, not just by index.
- Directory (business record) list screens are always server-paginated
  with filters (`stage`, `category_id`, `location_id`, `business_type`)
  pushed to SQL — never `SELECT *` then filter client-side.

## 6. What must not happen

- A local `dat_user`/`dat_staff` table or a flat `role` text column on
  any table — reuse `modules/identity` (§2.4).
- A second field-metadata table for this app — use `id_field_definition`.
- A second RBAC table or a packed-permissions column on `dat_business`.
- This app's tables declared under `database: OPZMAIN` instead of
  `OPZDATA`, or a Postgres `FOREIGN KEY` attempted across `OPZDATA` and
  `OPZMAIN`/`OPZUSER` ([doc 28](28-per-application-database-design.md)).
- Hardcoded category or location lists re-appearing in frontend
  constants — both are company-managed data (§2.1).
- `rating_average`/`reviews_count` writable through the general business
  update endpoint — only `BusinessReviewService` may write them.
- Public reviews or inquiries auto-publishing with no `status` gate.
- Unit test files under `apps/manage-my-data/**/src/test/` — they belong
  in `managemyopz-testing/01-unit/` (see requirement doc).
