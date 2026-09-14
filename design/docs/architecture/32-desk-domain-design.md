# 32 — Desk / Service Ticketing Domain Design (`apps/manage-my-desk`)

This document is **design only** — see [design/docs/requirement/manage-my-desk.md](../requirement/manage-my-desk.md)
for the implementation-ready requirement, and [apps/manage-my-desk/db/schema/](../../../apps/manage-my-desk/db/schema/)
for the DB schema YAML files (the one exception to "design only" in this drop).
This app's tables live in their own logical database, `OPZDESK`, per
[doc 28](28-per-application-database-design.md) — every `company_id →
company_information` reference, every `*_user_id → id_user` reference,
and the one optional `owning_org_unit_id → ppl_org_unit` reference below
are **cross-database**, enforced at the application layer, never a
Postgres `FOREIGN KEY`.

**Supersedes an earlier drop for this app** that was built from
`refer_mmo/opz-desk` and then explicitly undone at the user's request
("it has different references"). This design is built from a different,
more focused reference — see §1.

Reference source: the `desk` reference application (a separate,
not-cleanly-structured repository distinct from `refer_mmo/opz-desk`) was
read to extract *requirements*, not copied. No code, package name, or
table name from that app is reused as-is.

## 1. What this reference app actually is

Unlike this session's other reference apps, `desk`'s schema evidence
comes from **19 real Flyway migration files** (`V1`–`V19`) — ground-truth
DDL, not inferred from JPA annotations — cross-checked against 136 Java
files and confirming `@Entity` counts per module. This is a smaller,
tighter, and more coherent reference than `refer_mmo/opz-desk`'s
336-file sprawl: a **paid service-ticket desk for a physical-goods
fulfillment business** (its seed data is an ID-card/lanyard/badge
printer — categories "ID Card", "Lanyard", "Visiting Card", "UV Card").

Its most distinctive real feature, carried through this design, is a
**commercial-clearance gate**: work on a ticket does not proceed until
the client has either paid the required advance percentage of the
estimated cost, or has pre-approved credit terms. This is modeled
end-to-end in the reference — `TENANT_CLIENT_MASTER.IS_CREDIT_APPROVED`/
`CREDIT_LIMIT`, `TENANT_COMPANY_SETTINGS.ADVANCE_PAYMENT_PERCENTAGE`,
and `TICKET_HEADER.ESTIMATED_AMOUNT`/`ADVANCE_PERCENTAGE`/
`ADVANCE_PAID_AMOUNT`/`PAYMENT_MODE`/`CREDIT_PERIOD_DAYS`/
`CREDIT_DUE_AT`/`IS_COMMERCIAL_CLEARED` — and is a genuinely different
shape of service-desk than a typical IT helpdesk, worth preserving
faithfully rather than generalizing away.

reports/analytics/leaderboard/dashboard have **no `@Entity` classes at
all** in the reference (confirmed by grep) — real controllers, computed
from other tables at request time. This design keeps that: no new
tables for them (§5).

## 2. Domain model

Full column-level detail is in the requirement doc and the schema YAML
files. This section covers the fixes and generalizations applied over
the reference.

### 2.1 No local identity — including external client-portal logins

`AUTH_USER_MASTER` mixes login credentials (`email`, `password_hash`,
`failed_attempts`, `lock_time`) with a `USER_TYPE`
(`INTERNAL_EMPLOYEE` \| `CLIENT_USER`) and a nullable `CLIENT_ID` — the
same duplicated-identity anti-pattern already fixed in every
`apps/manage-my-*` app this session. Every "who" column in this design
(`reporter_user_id`, `assignee_user_id`, `affected_user_id`,
`author_user_id`, `agent_user_id`, `verified_by_user_id`, ...) is a
cross-database logical reference to `id_user.id` on `OPZUSER`.

The one genuinely new wrinkle this reference surfaces: **external
client-portal users are also platform logins**, not anonymous form
submitters (unlike the optional-login pattern used for public requesters
in `apps/manage-my-data`'s inquiries/reviews). `dsk_client_portal_user`
is a thin, one-row-per-login extension recording *which client account*
a login belongs to — the fact platform identity has no native concept
of — modeled the same way `apps/manage-my-market`'s
`mkt_agent_profile` extends `id_user` with app-specific data, never by
copying credentials.

### 2.2 `dsk_category` drops an ad hoc metadata mechanism for the real one

`CATEGORY_MASTER` bolts a single per-category dropdown directly onto the
table — `CATEGORY_TYPE`, `DROPDOWN_LABEL`, and `DROPDOWN_OPTIONS` (a
**comma-separated free-text string**, e.g. `"54mm x 86mm (CR80
Standard), 70mm x 100mm (Medium Badge), ..."`). This is exactly the
crude, hand-rolled version of what `id_field_definition`
([26](26-rbac-db-design.md) §2) already exists to do properly. This
design removes those three columns entirely: a category's custom fields
(e.g. "Card Size / Template" for the "ID Card" category) become
`id_field_definition` rows under
`form_id = "manage-my-desk.ticket.custom.<category_code>"`, with real
structured `allowed_values` JSON instead of a string a form has to
`.split(",")`, valued in `dsk_ticket_custom_field` — the identical EAV
pattern already used in every other `apps/manage-my-*` app this session.

### 2.3 `dsk_team_member` fixes a real missing foreign key

`TEAM_MEMBERS` stores `MEMBER_NAME` as free text with **no foreign key
to `AUTH_USER_MASTER` at all** — a team "member" and an actual login
user were never linked in the reference. `dsk_team_member.agent_user_id`
is a real (cross-database) reference to `id_user.id`.

### 2.4 `dsk_payment_transaction` generalizes a vendor-locked column pair

A later reference migration (`V8`) adds `STRIPE_SESSION_ID`/
`STRIPE_CUSTOMER_ID` directly to the payment table — hardcoding the
schema to one payment provider. This design uses
`gateway_session_id`/`gateway_customer_id` instead, so a different
gateway integration doesn't require a schema change (the same
"don't hardcode a single vendor into a column name" principle already
applied to `fin_tax_code` replacing `apps/manage-my-finance`'s
India-only `HsnSacCache`).

### 2.5 `dsk_sla_policy` / `dsk_ticket.sla_policy_id` completes an incomplete migration

The reference originally had `UNIQUE(company_id, severity)` on
`SLA_POLICY_MASTER` — one policy per severity, resolvable implicitly.
A later migration (`V18`) **drops that constraint** to allow multiple
named policies per severity (e.g. a stricter SLA for a VIP client) but
never adds anything to pick *which* policy applies to a given ticket —
the relaxation was left half-finished. `dsk_ticket.sla_policy_id` is an
explicit foreign key completing that intent: the two migrations' worth
of design are here, not four migrations of history to reverse-engineer.

### 2.6 What was already correct, and kept as designed

Two reference tables needed no fix at all, and are called out so the
"everything gets corrected" pattern from this session doesn't read as
mechanical: `TICKET_ATTACHMENT_MASTER` already stores a `file_url`, not
inline bytes, and `ACTIVITY_SCREENSHOT_LOG` already stores an
`S3_OBJECT_KEY`, not inline image data — both `dsk_ticket_attachment`
and `dsk_activity_screenshot` keep that shape unchanged.

### 2.7 `dsk_department` — optional cross-app org-structure enrichment

Same optional pattern as this app's earlier `dsk_group.owning_org_unit_id`
design and `apps/manage-my-market`'s `mkt_agent_profile.linked_person_id`:
`dsk_department.owning_org_unit_id` may reference `apps/manage-my-
people`'s `ppl_org_unit` (`OPZPEOPLE`) if that app happens to be
installed, never required.

## 3. GUI metadata — confirmed mechanism, no new table

Same mechanism as every `apps/manage-my-*` design this session: every
field-level requirement is served by `id_field_definition`
([26](26-rbac-db-design.md) §2) through `FormEnvelope`
([22](22-common-fields-forms-fk.md) §3). `dsk_ticket_custom_field`
mirrors the EAV pattern already established in the other three apps —
and, as §2.2 covers, directly replaces a real anti-pattern the reference
itself had (a hand-rolled dropdown mechanism), making this app the
clearest evidence yet in this session for **why** the platform's
metadata-driven approach exists: the reference tried to solve the same
problem ad hoc and produced something worse (unstructured string
parsing, no role visibility, no mandatory/format rules) than what
`id_field_definition` already provides.

## 4. RBAC / ABAC

`AUTH_ROLE_PERMISSION_MASTER` in the reference (`role_key`, `page_id`,
`can_view`/`can_create`/`can_edit`/`can_delete`/`can_assign`/
`can_export`) is strong independent validation of this platform's own
RBAC-in-DB design ([26](26-rbac-db-design.md) §2) — a per-role,
per-screen permission matrix with distinct CRUD-plus-extra actions is
exactly what `id_role_permission`'s packed-letter model already
generalizes. It is **not** reproduced as a local table; `can_assign`
maps to this app's `a` (approve/assign) letter and `can_export` maps to
`io.export` gated by `v` ([doc 22](22-common-fields-forms-fk.md) §8),
per [doc 18](18-identity-rbac-abac-oauth2.md) §3.1's existing "import/
export/bulk reuse these letters" rule.

Feature ids declared for `module_id = "manage-my-desk"` (≤4 chars,
[18](18-identity-rbac-abac-oauth2.md) §3.1, cap 32/app):

| Feature id | Title | Covers |
|---|---|---|
| `tkt` | Tickets | `dsk_ticket`, `dsk_ticket_comment`, `dsk_ticket_status_history`, `dsk_ticket_attachment`, `dsk_ticket_custom_field` |
| `sla` | SLA | `dsk_sla_policy`, `dsk_sla_clock_log` |
| `pay` | Payments | `dsk_payment_transaction` (commercial-clearance verification) |
| `act` | Activity monitoring | `dsk_activity_screenshot`, `dsk_timesheet_entry` |
| `tea` | Teams | `dsk_team`, `dsk_team_member` |
| `cli` | Clients | `dsk_client_account`, `dsk_client_portal_user` |
| `cat` | Categories | `dsk_category` |
| `cfg` | Company settings | `dsk_company_setting`, `dsk_department` (admin-only) |

Enforcement is the standard two-layer stack from
[Rule 4](../requirement/IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level).
A client-portal login's `tkt.v`/`tkt.c` is narrowed by ABAC to
`resource.client_id == dsk_client_portal_user(user.id).client_id` —
a client sees and raises tickets only for their own client account,
never another client's. Full role list and seed rows are in the
requirement doc's "RBAC in DB" section.

## 5. Explicitly out of scope (with rationale)

| Reference sub-domain | Disposition |
|---|---|
| `NOTIFICATION_DISPATCH_LOG` (channels: `EMAIL`, `WHATSAPP`) | Shared, cross-cutting bounded context (`notifications`, [doc 00 §7](00-system-overview.md#7-bounded-contexts-feature-modules)) — same disposition as every other app's notification-log exclusion this session. The `WHATSAPP` channel is worth carrying as design input for that future shared module, not rebuilt here. |
| Reports / Analytics / Leaderboard / Dashboard (`ReportController`, `AnalyticsController`, `DashboardMetricsDto`, `LeaderboardEntryDto`) | No backing `@Entity` in the reference — real controllers computing over other tables at request time. This design adds no schema for them either; they are SQL aggregate queries over `dsk_ticket`/`dsk_sla_clock_log`/`dsk_activity_screenshot`/`dsk_timesheet_entry` at the service layer, same "computed, not stored" decision already made for leaderboards in `apps/manage-my-market`'s referrer aggregates (there, stored because per-row list-screen reads justified it; here, dashboard-shaped reads don't). |
| Local auth/RBAC (`AUTH_USER_MASTER`, `AUTH_ROLE_MASTER`, `AUTH_USER_ROLE_MAPPING`, `AUTH_ROLE_PERMISSION_MASTER`) | Reuse `modules/identity` (doc 18, doc 26) entirely — see §2.1, §4. |
| Actual email/WhatsApp send mechanics behind the (deferred) notification log | Belongs to `modules/mail` (doc 23) and a future messaging-channel integration — not yet built, same class of gap as every other app's payment-gateway/mail dependency this session. |

## 6. Performance, optimization, memory (domain-specific notes)

Beyond the universal rules in
[Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal):

- `dsk_category` is a read-heavy, write-rare catalog cached in
  `CacheClient` per company, same pattern as every other app's category/
  taxonomy tables this session.
- `dsk_ticket_status_history`, `dsk_ticket_comment`, and
  `dsk_sla_clock_log` are unbounded-growth append-only child tables —
  never joined on a ticket list screen, loaded paginated only on a
  single ticket's detail view.
- `dsk_ticket.advance_paid_amount`/`is_commercial_cleared` are
  maintained columns, recomputed by the service layer in the same
  transaction as a `dsk_payment_transaction` status change to
  `VERIFIED` — the commercial-clearance check on ticket-detail load
  never runs a live `SUM` over payment rows.
- `dsk_activity_screenshot` capture volume is inherently high-frequency;
  list/detail reads select `storage_object_key` only, never the image —
  the object itself streams from storage on direct request, same
  discipline as every attachment table this session.
- Ticket directory list screens are always server-paginated with
  filters (`status`, `client_id`, `assignee_user_id`, `mode_of_enquiry`,
  `payment_mode`) pushed to SQL.

## 7. What must not happen

- A local `dsk_user`/`dsk_role` table or a `user_type` column anywhere —
  reuse `modules/identity`; distinguish a client login only via
  `dsk_client_portal_user` (§2.1).
- A comma-separated string dropdown re-appearing on `dsk_category` —
  use `id_field_definition` (§2.2).
- A team member row with no real `agent_user_id` foreign key (§2.3).
- Payment gateway columns named after one vendor (`stripe_*`,
  `razorpay_*`, ...) — use `gateway_session_id`/`gateway_customer_id`
  (§2.4).
- `dsk_ticket.advance_paid_amount`/`is_commercial_cleared` written
  directly through the general ticket update endpoint instead of derived
  from `dsk_payment_transaction` verification.
- This app's tables declared under `database: OPZMAIN` instead of
  `OPZDESK`, or a Postgres `FOREIGN KEY` attempted across `OPZDESK` and
  `OPZMAIN`/`OPZUSER`/`OPZPEOPLE` ([doc 28](28-per-application-database-design.md)).
- Unit test files under `apps/manage-my-desk/**/src/test/` — they
  belong in `managemyopz-testing/01-unit/` (see requirement doc).
