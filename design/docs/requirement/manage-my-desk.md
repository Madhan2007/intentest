# Requirement: Manage My Desk

**App:** `manage-my-desk` (new, `apps/manage-my-desk`)
**DB target:** `opzdesk` (per-company, own logical database — [doc 28](../architecture/28-per-application-database-design.md))
**Access level:** Role-based per feature (see RBAC in DB section) — no single fixed role
**Architecture ref:** [doc 32](../architecture/32-desk-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md) · [doc 26 §2](../architecture/26-rbac-db-design.md) · [doc 22](../architecture/22-common-fields-forms-fk.md)
**Reference source:** the `desk` reference application (requirements extracted only — no code, package, or table name reused; distinct from an earlier, since-undone drop built from `refer_mmo/opz-desk` — see [doc 32 §1](../architecture/32-desk-domain-design.md#1-what-this-reference-app-actually-is))

---

## What It Does

A paid service-ticket desk: tickets move from intake through a
**commercial-clearance gate** (the client must either pay a required
advance percentage of the estimated cost or hold pre-approved credit
terms before work proceeds), get worked by internal staff or routed
through client-portal logins, tracked against a company-configurable SLA
with a real pause/resume clock, and logged with employee activity
screenshots and timesheets. Matches the app catalog's own description
("Organize tasks, work, and daily operations") for a service business
that bills 

See [doc 32 §1](../architecture/32-desk-domain-design.md#1-what-this-reference-app-actually-is)
for why this design is built from ground-truth Flyway migrations (19
files) rather than inferred JPA fields, and
[doc 32 §2](../architecture/32-desk-domain-design.md#2-domain-model)
for the fixes applied over the reference (a hand-rolled, comma-separated
dropdown mechanism replaced with real `id_field_definition` metadata; a
team-membership table with no real foreign key, fixed; a vendor-locked
payment-gateway column pair generalized; an SLA-policy migration that
relaxed a uniqueness constraint without ever adding the field needed to
use that relaxation, completed).

This app owns no identity or RBAC data of its own. Every person
reference — including **external client-portal logins**, not just
internal staff — is a cross-database logical link to `modules/identity`'s
`id_user`; a login is marked as belonging to a client only via the thin
`dsk_client_portal_user` extension, never a duplicated user table. See
Dependencies and
[doc 32 §2.1](../architecture/32-desk-domain-design.md#21-no-local-identity--including-external-client-portal-logins).

**Design-only drop:** per instruction, this requirement and its
architecture doc are design artifacts. The **only** files actually created
in this drop are the DB schema YAML files under
[`apps/manage-my-desk/db/schema/`](../../../apps/manage-my-desk/db/schema/).
Everything else in "Files to Create" below is implementation for later.

---

## Entities & Tables

All tables are in `opzdesk` — this app's **own** logical database, per
[doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application),
not the shared `opzmain`. `company_id`, every `*_user_id` column, and the
one optional `owning_org_unit_id` are therefore **cross-database** (to
`opzmain`, `opzuser`, and optionally `opzpeople`), enforced at the
application layer per
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
never a Postgres `FOREIGN KEY`. Full column definitions, indexes, and
foreign keys are the schema YAML files under
`apps/manage-my-desk/db/schema/` — this section is the human-readable
summary.

### Clients & tenancy

| Table | Purpose |
|---|---|
| `dsk_client_account` | The external client company being serviced — `is_credit_approved`/`credit_limit` drive the commercial-clearance gate |
| `dsk_client_portal_user` | Marks a login (`id_user`) as belonging to one client account — no local user table |
| `dsk_department` | Simple per-company department list; optional cross-app link to `ppl_org_unit` |
| `dsk_company_setting` | Business hours/work days (SLA business-hours calculations) and default advance-payment terms |

### Catalog

| Table | Purpose |
|---|---|
| `dsk_category` | Ticket category; custom fields defined via `id_field_definition`, not an ad hoc column |
| `dsk_team` / `dsk_team_member` | Support team and its agents (real `agent_user_id` FK) |
| `dsk_sla_policy` | SLA target by severity; `dsk_ticket.sla_policy_id` picks which policy applies when multiple exist per severity |

### Tickets

| Table | Purpose |
|---|---|
| `dsk_ticket` | Core record — commercial-clearance fields, SLA fields, category/team/client/reporter/assignee/affected-user references |
| `dsk_ticket_comment` | Internal or client-visible comment/reply |
| `dsk_ticket_status_history` | Append-only status-transition audit trail |
| `dsk_ticket_attachment` | File reference (`file_url`), never inline bytes |
| `dsk_ticket_custom_field` | EAV values for a category's custom fields; definitions in `id_field_definition` |
| `dsk_ticket_code_sequence` | Atomic counter generating `dsk_ticket.ticket_number` |

### SLA, payments & activity

| Table | Purpose |
|---|---|
| `dsk_sla_clock_log` | Pause/resume clock-state transitions with accumulated active seconds |
| `dsk_payment_transaction` | Advance/commercial payment against a ticket — manual receipt verification or online gateway |
| `dsk_activity_screenshot` | Employee activity-monitoring capture (`storage_object_key`, never inline image bytes) |
| `dsk_timesheet_entry` | Per-ticket, per-day logged work minutes |

---

## API Endpoints

Base path: `/api/v1/opzhub/manage-my-desk`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/tickets` | Create a ticket (forces `status = NEW`, `is_commercial_cleared = false`) |
| `POST` | `/tickets/read` | List tickets (paginated, filterable by `status`, `client_id`, `assignee_user_id`, `payment_mode`) or read by `id` |
| `PUT` | `/tickets` | Update ticket details |
| `PUT` | `/tickets/{id}/status` | Transition status; writes a `dsk_ticket_status_history` row |
| `GET` | `/tickets/code/preview` / `POST /tickets/code/reserve` | Ticket number generation |
| `POST` | `/tickets/{id}/comments` / `.../read` | Comment CRUD |
| `POST` | `/tickets/{id}/attachments` / `.../read` / `DELETE` | Attachment CRUD |
| `POST` | `/tickets/{id}/custom-fields` / `.../read` | Custom field values (definitions from `GET /forms/manage-my-desk.ticket.custom.<category_code>`) |
| `POST` | `/tickets/{id}/payments` | Record a payment transaction |
| `PUT` | `/payments/{id}/verify` / `PUT /payments/{id}/reject` | Staff verification of a manual payment; recomputes `advance_paid_amount`/`is_commercial_cleared` |
| `POST` | `/payments/read` | List payment transactions |
| `POST` | `/tickets/{id}/sla/pause` / `PUT /tickets/{id}/sla/resume` | SLA clock control; writes a `dsk_sla_clock_log` row |
| `POST` | `/sla-policies` / `.../read` / `PUT` / `DELETE` | SLA policy CRUD (admin) |
| `POST` | `/categories` / `.../read` / `PUT` / `DELETE` | Category CRUD (admin) |
| `POST` | `/teams` / `.../read` / `PUT` / `DELETE` | Team CRUD (admin) |
| `POST` | `/teams/{id}/members` / `.../read` / `DELETE` | Team membership |
| `POST` | `/clients` / `.../read` / `PUT` | Client account CRUD |
| `POST` | `/clients/{id}/portal-users` / `.../read` / `DELETE` | Client-portal login mapping (admin) |
| `POST` | `/tickets/{id}/activity-screenshots` / `.../read` | Activity capture ingest + list |
| `POST` | `/timesheet-entries` / `.../read` / `PUT` / `DELETE` | Timesheet CRUD |
| `GET`/`PUT` | `/company-settings` | Business hours/default terms (admin-only) |

All responses use the standard `ApiEnvelope<T>` wrapper with
`correlation_id`.

---

## SQL Commands

Named SQL files in `apps/manage-my-desk/db/commands/`:

```
ticket.insert.sql / .find_by_id.sql / .find_by_company_and_number.sql / .list_paged.sql / .update.sql / .update_status.sql
ticket_code_sequence.reserve_next.sql   -- single UPDATE ... RETURNING, no race
ticket_comment.insert.sql / .list_by_ticket.sql
ticket_status_history.insert.sql / .list_by_ticket.sql
ticket_attachment.insert.sql / .list_by_ticket.sql / .delete.sql
ticket_custom_field.upsert.sql / .list_by_ticket.sql
payment_transaction.insert.sql / .verify.sql / .reject.sql / .list_by_ticket.sql
ticket.recompute_commercial_clearance.sql  -- SUM(VERIFIED payments) for one ticket_id, runs inside the verify transaction
sla_policy.list_by_company.sql
sla_clock_log.insert.sql / .list_by_ticket.sql
category.list_by_company.sql
team.list_by_company.sql / team_member.list_by_team.sql
client_account.insert.sql / .update.sql / .list_paged.sql
client_portal_user.insert.sql / .find_by_user_id.sql / .delete.sql
department.list_by_company.sql
company_setting.find_by_company.sql / .upsert.sql
activity_screenshot.insert.sql / .list_by_agent_and_ticket.sql
timesheet_entry.insert.sql / .update.sql / .list_by_agent_and_date.sql
```

`ticket_code_sequence.reserve_next.sql` must be a single atomic
`UPDATE ... RETURNING` (`ON CONFLICT` insert-on-missing) — never a
`SELECT` then `UPDATE`, same requirement as every other app's code
sequence this session.

---

## Files to Create

```
apps/manage-my-desk/
├── module.yaml                                              ← NEW (design only, not created this drop)
├── backend/src/main/java/com/managemyopz/apps/managemydesk/
│   ├── ManageMyDeskAutoConfiguration.java                   ← NEW
│   ├── api/
│   │   ├── TicketController.java
│   │   ├── TicketCommentController.java
│   │   ├── TicketAttachmentController.java
│   │   ├── PaymentTransactionController.java
│   │   ├── SlaPolicyController.java
│   │   ├── SlaClockController.java
│   │   ├── CategoryController.java
│   │   ├── TeamController.java
│   │   ├── ClientAccountController.java
│   │   ├── ActivityScreenshotController.java
│   │   ├── TimesheetController.java
│   │   ├── CompanySettingController.java
│   │   └── dto/                                              (Create/Update/Response DTOs per entity)
│   ├── application/
│   │   ├── TicketService.java
│   │   ├── TicketCodeSequenceService.java
│   │   ├── TicketStatusService.java                          (status transitions + history logging)
│   │   ├── CommercialClearanceService.java                   (advance_paid_amount / is_commercial_cleared maintenance)
│   │   ├── SlaEngineService.java                              (due-date computation, clock pause/resume)
│   │   ├── PaymentTransactionService.java
│   │   ├── CategoryService.java
│   │   ├── TeamService.java
│   │   ├── ClientAccountService.java
│   │   ├── ActivityScreenshotService.java
│   │   ├── TimesheetService.java
│   │   ├── CompanySettingService.java
│   │   └── ManageMyDeskConstants.java
│   ├── domain/
│   │   ├── Ticket.java
│   │   ├── ClientAccount.java
│   │   ├── SlaPolicy.java
│   │   └── ... (one record per entity)
│   └── data/
│       ├── TicketRepository.java / DataClientTicketRepository.java
│       └── ... (one repository pair per entity)
├── db/
│   ├── schema/                                               ← EXISTS (this drop) — 18 files, database: OPZDESK
│   │   ├── dsk_client_account.yaml
│   │   ├── dsk_client_portal_user.yaml
│   │   ├── dsk_department.yaml
│   │   ├── dsk_company_setting.yaml
│   │   ├── dsk_category.yaml
│   │   ├── dsk_team.yaml
│   │   ├── dsk_team_member.yaml
│   │   ├── dsk_sla_policy.yaml
│   │   ├── dsk_sla_clock_log.yaml
│   │   ├── dsk_ticket.yaml
│   │   ├── dsk_ticket_comment.yaml
│   │   ├── dsk_ticket_status_history.yaml
│   │   ├── dsk_ticket_attachment.yaml
│   │   ├── dsk_ticket_custom_field.yaml
│   │   ├── dsk_ticket_code_sequence.yaml
│   │   ├── dsk_payment_transaction.yaml
│   │   ├── dsk_activity_screenshot.yaml
│   │   └── dsk_timesheet_entry.yaml
│   └── commands/                                             ← NEW (see SQL Commands above)
├── forms/                                                    ← NEW (catalog defaults, doc 22 §7)
│   ├── ticket.create.yaml
│   ├── ticket.edit.yaml
│   ├── ticket.list.yaml
│   ├── ticket-custom.yaml                                     (per-category, see GUI Metadata Design)
│   ├── category.edit.yaml
│   ├── team.edit.yaml
│   ├── sla-policy.edit.yaml
│   ├── client-account.edit.yaml
│   ├── payment-transaction.edit.yaml
│   ├── timesheet-entry.edit.yaml
│   └── company-setting.edit.yaml
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   ├── menu.ts
│   ├── manageMyDeskConstants.ts
│   └── pages/
│       ├── TicketListPage.tsx
│       ├── TicketDetailPage.tsx                              (tabs: comments, history, attachments, payments, SLA)
│       ├── CategoryAdminPage.tsx
│       ├── TeamAdminPage.tsx
│       ├── SlaPolicyAdminPage.tsx
│       ├── ClientAccountListPage.tsx
│       ├── TimesheetPage.tsx
│       ├── ActivityMonitorPage.tsx
│       └── CompanySettingPage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        ├── ticket_list_page.dart
        └── ticket_detail_page.dart
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| `ticket_number` unique per company, generated (not user-typed) | `ticket_code_sequence.reserve_next.sql`; DB unique index as final guard |
| A new ticket always starts `status = NEW`, `is_commercial_cleared = false`, regardless of client input | `TicketService.create()` forces both server-side |
| `advance_paid_amount`/`is_commercial_cleared` are never client-writable | Excluded from the general update DTO; only `CommercialClearanceService` recomputes them, in the same transaction as a payment's `VERIFIED` transition |
| `is_commercial_cleared` becomes true when `advance_paid_amount >= estimated_amount * advance_percentage / 100` (ADVANCE mode) or `dsk_client_account.is_credit_approved` is true and `credit_due_at` has not passed (CREDIT mode) | `CommercialClearanceService` — evaluated on every payment verification and on ticket creation |
| `credit_due_at` is computed as ticket creation date + `credit_period_days` when `payment_mode = CREDIT` | `TicketService.create()` |
| Every SLA clock transition writes a `dsk_sla_clock_log` row; `dsk_ticket.sla_status` is derived from the latest log row, not set directly | `SlaEngineService` |
| A client-portal login (`dsk_client_portal_user`) may only see/raise tickets for its own `client_id` | ABAC — see API-Level RBAC/ABAC |
| Every table's `company_id` and every `*_user_id` are validated against the owning database before write, never assumed present | Service-layer existence check per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication), same `Conflict`/`err: fk` shape as a same-database FK |

---

## Dependencies

- `identity` module — session, RBAC tables (`id_role`, `id_user_role`,
  `id_role_permission`, `id_user_permission`, `id_field_definition` from
  [doc 26](../architecture/26-rbac-db-design.md)), and a registered named
  command this app calls cross-database (`identity.find_user_by_id`,
  `opzuser`) to resolve staff/client display names. **No schema change
  to `identity` is required.**
- `company-setup` — `company_information` (`opzmain`) must exist for the
  cross-database `company_id` reference on every table in this app.
- **Optional:** `apps/manage-my-people` — if installed,
  `dsk_department.owning_org_unit_id` may reference its `ppl_org_unit`.
  Not required; stays `null` cleanly if that app is absent
  ([doc 22 §6.2](../architecture/22-common-fields-forms-fk.md#62-when-not-to-use-a-postgres-fk-logical-fk)).
- **New platform capability:** `OPZDESK` as a provisioned logical
  database (connection pool, migration target, backup schedule) —
  [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).
- **Not built, referenced only as a future dependency:** actual payment-
  gateway capture behind `dsk_payment_transaction.gateway_session_id`/
  `gateway_customer_id`, and email/WhatsApp send mechanics behind the
  (deferred) notification log — same class of not-yet-built external-
  integration dependency as every other app's mail/gateway gap this
  session.
- Employee activity-screenshot capture (`dsk_activity_screenshot`)
  assumes a client-side/agent-side capture agent exists — not designed
  in this drop; this table is the server-side storage contract for it.
- `modules/apps` — this app already has a catalog entry
  (`app_key: manage-my-desk`, `opz-002`, category `desk`) in
  `modules/apps/db/seed/application_catalog.yaml`; no seed change is
  needed. `ApplicationFolderPresence` still requires
  `apps/manage-my-desk/{frontend,backend,mobile}` to exist before the
  app can be licensed — not performed in this drop (implementation only).

---

## GUI Metadata Design

Every screen renders from `id_field_definition` via `GET
/api/v1/opzhub/forms/{form_id}` ([doc 22](../architecture/22-common-fields-forms-fk.md) §3).

### Screen: Ticket (`manage-my-desk.ticket.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `client_id` | Client | `lookup` | Yes | `fk: { res: "manage-my-desk.client-account" }` | `tkt` `c` | Locked after create |
| `category_id` | Category | `lookup` | Yes | `fk: { res: "manage-my-desk.category" }` | `tkt` `c` | Selecting a category loads that category's custom-field form (`manage-my-desk.ticket.custom.<category_code>`) inline |
| `severity` | Severity | `select` | Yes | `SEV_1`,`SEV_2`,`SEV_3`,`SEV_4` | `tkt` `c`/`u` | Selecting resolves and shows the matching `sla_policy_id` as a read-only hint |
| `payment_mode` | Payment Mode | `radio` | Yes | `ADVANCE`, `CREDIT` | `tkt` `c` | `CREDIT` selectable only if the selected client has `is_credit_approved = true`; `advance_percentage` field hidden when `CREDIT` |
| `is_commercial_cleared` | Commercially Cleared | `display` | — | Read-only, computed | `tkt` `v` | Status badge; "Record Payment" subaction visible only while false |
| `status` | Status | `select` | Yes | `NEW`,`IN_PROGRESS`,`PENDING`,`ON_HOLD`,`RESOLVED`,`CLOSED`,`CANCELLED` | `tkt` `u` only, `mode: view` on this form | Driven by a dedicated "Change Status" action, never free-editable here |

### Screen: Category Custom Fields (`manage-my-desk.ticket.custom.<category_code>`, per category)

Example for category `id-card` (mirrors the reference app's real seed
data):

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `card_size` | Card Size / Template (mm) | `select` | Yes | `54mm x 86mm (CR80 Standard)`, `70mm x 100mm (Medium Badge)`, `102mm x 140mm (Event Pass)` | `tkt` `c`/`u` | — |

This is a structured `id_field_definition` row (`allowed_values` JSON),
not the reference app's `DROPDOWN_OPTIONS` comma-separated string.

### Screen: Payment Transaction (`manage-my-desk.payment-transaction.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `payment_method` | Method | `select` | Yes | `ONLINE_GATEWAY`,`BANK_TRANSFER`,`CHEQUE` | `pay` `c` | `receipt_url` upload field visible only for `BANK_TRANSFER`/`CHEQUE` |
| `status` | Status | `select` | Yes | `PENDING`,`VERIFIED`,`REJECTED` | `pay` `u`, `role_visibility: {"finance_clerk":"readonly","desk_admin":"edit"}` | Setting `VERIFIED` triggers `CommercialClearanceService` recomputation server-side |

### Screen: Ticket List (directory)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `ticket_number` | Ticket # | `text` | Yes | `tkt` `v` |
| `title` | Title | `text` | Yes | `tkt` `v` |
| `client_id` (joined) | Client | `text` | Yes | `tkt` `v` |
| `status` | Status | `status-badge` | Yes | `tkt` `v` |
| `severity` | Severity | `badge` | Yes | `tkt` `v` |
| `is_commercial_cleared` | Cleared | `boolean-badge` | Yes | `tkt` `v` |
| — | Actions | `actions` | No | `tkt` `v` | View, Edit (`u`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import: []`
(tickets always go through the code-reservation + commercial-clearance
flow, never bulk import), `bulk: { u: true, d: false }`.

### Metadata-Driven Rules

- `payment_mode = CREDIT` is only offered when the selected client's
  `is_credit_approved` is true — a FormEnvelope `when` condition
  ([doc 22 §4.3](../architecture/22-common-fields-forms-fk.md#43-depends-on-still-from-be))
  driven by a server-side lookup, not a hardcoded `if` in the component.
- `status` is never a free-editable field on the edit form — the detail
  page always uses a dedicated "Change Status" action, same pattern as
  `apps/manage-my-market`'s lead `owner_user_id` reassignment and
  `apps/manage-my-finance`'s invoice `status`.
- Category selection dynamically loads that category's custom-field
  form — the ticket page never hardcodes a per-category field list.

---

## Directory Placement

```
apps/manage-my-desk/            ← Application-specific (sold, licensed app)
│                                   Own logical database: OPZDESK (doc 28 §2)
├── backend/
├── frontend/
│   └── pages/                  ← Uses field primitives from common/frontend, not its own copies
├── mobile/
├── forms/                      ← Catalog defaults; runtime = id_field_definition (doc 26) + FormEnvelope (doc 22)
└── db/
    ├── schema/                 ← EXISTS (this drop) — 18 files, database: OPZDESK
    └── commands/

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reused for client_id, category_id, sla_policy_id
│   └── ... (no new control types needed — this app introduces no field kind not already in doc 22 §2)
├── theme/tokens.ts               ← Reused; no new colors
└── icons/                        ← "desk" icon_key already exists in the catalog; no new icon needed
```

No changes to `modules/identity/` or `modules/apps/` are required — see
Dependencies above.

**Rules:**
- No module-local reimplementation of `LookupField`, `DataTable`, or any
  field control ([doc 22](../architecture/22-common-fields-forms-fk.md) §10).
- `dsk_category`/`dsk_sla_policy`/`dsk_client_account` lookups are
  reused by every screen in this app via the kernel `LookupField` +
  `/lookup/{res}` API — never a per-screen hardcoded option list.

---

## Constants

### Backend (`apps/manage-my-desk/backend/.../ManageMyDeskConstants.java`)

```java
public static final String TICKET_STATUS_NEW              = "NEW";
public static final String TICKET_STATUS_IN_PROGRESS       = "IN_PROGRESS";
public static final String TICKET_STATUS_PENDING           = "PENDING";
public static final String TICKET_STATUS_ON_HOLD           = "ON_HOLD";
public static final String TICKET_STATUS_RESOLVED          = "RESOLVED";
public static final String TICKET_STATUS_CLOSED            = "CLOSED";
public static final String TICKET_STATUS_CANCELLED         = "CANCELLED";

public static final String SEVERITY_SEV_1                  = "SEV_1";
public static final String SEVERITY_SEV_2                  = "SEV_2";
public static final String SEVERITY_SEV_3                  = "SEV_3";
public static final String SEVERITY_SEV_4                  = "SEV_4";

public static final String PAYMENT_MODE_ADVANCE             = "ADVANCE";
public static final String PAYMENT_MODE_CREDIT               = "CREDIT";

public static final String PAYMENT_STATUS_PENDING           = "PENDING";
public static final String PAYMENT_STATUS_VERIFIED           = "VERIFIED";
public static final String PAYMENT_STATUS_REJECTED           = "REJECTED";

public static final int    TICKET_NUMBER_MAX_LEN            = 32;
```

### Frontend (`apps/manage-my-desk/frontend/manageMyDeskConstants.ts`)

```typescript
export const DESK_API_BASE             = "/api/v1/opzhub/manage-my-desk";
export const TICKET_LIST_HEADING       = "Tickets";
export const SEVERITY_OPTIONS = [
  { value: "SEV_1", label: "SEV-1 — Critical" },
  { value: "SEV_2", label: "SEV-2 — Major"    },
  { value: "SEV_3", label: "SEV-3 — Standard" },
  { value: "SEV_4", label: "SEV-4 — Minor"    },
];
export const PAYMENT_MODE_OPTIONS = [
  { value: "ADVANCE", label: "Advance Payment" },
  { value: "CREDIT",  label: "Approved Credit" },
];
```

Colors, spacing, and icon keys stay in `common/frontend/src/theme/tokens.ts`
and `common/frontend/src/icons/` — not duplicated here
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).

---

## Optimization, Performance & Memory

See [doc 32 §6](../architecture/32-desk-domain-design.md#6-performance-optimization-memory-domain-specific-notes)
for the domain-specific reasoning. Summary of concrete rules:

### Performance
- `dsk_category` is cached per company in `CacheClient` (explicit TTL,
  evicted on write).
- Ticket directory list is always server-paginated with filters pushed
  into `ticket.list_paged.sql`.
- `ticket_code_sequence.reserve_next.sql` is one atomic
  `UPDATE ... RETURNING` — no read-then-write race.
- `advance_paid_amount`/`is_commercial_cleared` are maintained — the
  ticket detail view never runs a live `SUM` over payment rows.

### Memory
- **React:** `TicketDetailPage` tabs (comments, history, attachments,
  payments, SLA) fetch their own data lazily on tab-select — same
  pattern as every other `apps/manage-my-*` detail page this session.
- **Java:** `dsk_ticket_status_history`/`dsk_sla_clock_log` stream
  paginated — never loaded as a full list into memory.
- **Activity screenshots:** list/detail reads select
  `storage_object_key` only; the image itself streams from object
  storage on direct request, never held as row payload.

### Optimization
- SLA due-date computation runs at write time (status change, pause,
  resume), not recomputed on every ticket read.
- Bulk update on the ticket directory touches only `mode: edit` fields
  per doc 22 §10.2 — never a blind full-row rewrite.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> Desk domain design: [doc 32](../architecture/32-desk-domain-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-desk/` (mirroring
the `01-unit/modules/<name>/` convention from
[Rule 1](IMPLEMENTATION_RULES.md#rule-1--unit-test-cases-separate-repo),
extended for the `apps/` vs `modules/` split, same as every other
`apps/manage-my-*` requirement doc). No test files under
`apps/manage-my-desk/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `TicketServiceTest` | Create forces `NEW`/uncleared state; `ticket_number` reservation atomicity; `credit_due_at` computation |
| `CommercialClearanceServiceTest` | Advance-mode threshold math; credit-mode approval + overdue check; recomputes only on `VERIFIED` payments |
| `SlaEngineServiceTest` | Pause/resume accumulates `accumulated_active_seconds` correctly; business-hours-only clocks respect `dsk_company_setting` |
| `TeamMemberServiceTest` | Membership requires a real `agent_user_id`; duplicate membership rejected |
| `TicketControllerTest` | RBAC annotation enforcement per feature id; client-portal ABAC scoping to own `client_id` |

### RBAC in DB

Feature ids: `tkt`, `sla`, `pay`, `act`, `tea`, `cli`, `cat`, `cfg` (full
table and rationale: [doc 32 §4](../architecture/32-desk-domain-design.md#4-rbac--abac)).

Starter roles seeded by this app's migration — five roles, mapped from
the reference's `AUTH_ROLE_MASTER` seed (`ROLE_SUPER_ADMIN`/
`ROLE_COMPANY_ADMIN`/`ROLE_ADMIN`/`ROLE_SUB_ADMIN` collapse to this
app's own `desk_admin`, since the platform/company-tier admin
distinction is already handled by `modules/identity`, not re-litigated
per app):

```sql
INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('desk_admin',        'Desk Administrator',   true),
  ('desk_support_lead',  'Support Manager',       true),
  ('desk_support_agent', 'Support Engineer',      true),
  ('desk_client_admin',  'Client Administrator',  true),
  ('desk_client_user',   'Client User',           true);

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('desk_admin',         'manage-my-desk', 'tkt', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'sla', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'pay', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'act', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'tea', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'cli', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'cat', 'vcud'),
  ('desk_admin',         'manage-my-desk', 'cfg', 'vu'),
  ('desk_support_lead',  'manage-my-desk', 'tkt', 'vcua'),
  ('desk_support_lead',  'manage-my-desk', 'sla', 'v'),
  ('desk_support_lead',  'manage-my-desk', 'pay', 'vu'),
  ('desk_support_lead',  'manage-my-desk', 'act', 'v'),
  ('desk_support_lead',  'manage-my-desk', 'tea', 'vu'),
  ('desk_support_agent', 'manage-my-desk', 'tkt', 'vu'),
  ('desk_support_agent', 'manage-my-desk', 'act', 'vc'),
  ('desk_client_admin',  'manage-my-desk', 'tkt', 'vc'),
  ('desk_client_admin',  'manage-my-desk', 'pay', 'vc'),
  ('desk_client_user',   'manage-my-desk', 'tkt', 'vc');
```

`desk_support_agent`'s `tkt.u` is further narrowed by ABAC to
`resource.assignee_user_id == user.id`. `desk_client_admin`/
`desk_client_user`'s `tkt.*` are narrowed to
`resource.client_id == dsk_client_portal_user(user.id).client_id` — a
client sees only their own tickets, and `desk_client_user` (unlike
`desk_client_admin`) never gets `pay.c` (only a client admin submits
payment proof). Company admins may layer `id_user_permission`
GRANT/REVOKE rows on top of these five starter roles without creating
new roles, per
[Rule 2](IMPLEMENTATION_RULES.md#rule-2--rbac-in-db-with-optimized-tables).

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `manage-my-desk.ticket.create` | Create Ticket |
| `manage-my-desk.ticket.edit` | Edit Ticket |
| `manage-my-desk.ticket.list` | Ticket Directory |
| `manage-my-desk.ticket.custom.<category_code>` | Per-category custom fields (one form per `dsk_category` row) |
| `manage-my-desk.category.edit` | Category Admin |
| `manage-my-desk.team.edit` | Team Admin |
| `manage-my-desk.sla-policy.edit` | SLA Policy Admin |
| `manage-my-desk.client-account.edit` | Client Account Admin |
| `manage-my-desk.payment-transaction.edit` | Record/Verify Payment |
| `manage-my-desk.timesheet-entry.edit` | Timesheet Entry |
| `manage-my-desk.company-setting.edit` | Company Settings (admin-only) |

Example migration row (`card_size` custom field for category `id-card`,
DB-driven allowed values — the direct replacement for the reference
app's `DROPDOWN_OPTIONS` string):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, display_order, allowed_values
) VALUES (
  'manage-my-desk.ticket.custom.id-card', 'card_size', 'Card Size / Template (mm)', 'select',
  true, 1,
  '[{"value":"54x86","label":"54mm x 86mm (CR80 Standard)"},
    {"value":"70x100","label":"70mm x 100mm (Medium Badge)"},
    {"value":"102x140","label":"102mm x 140mm (Event Pass)"}]'::jsonb
);
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /tickets` | `SessionAuthFilter` (401) | `tkt.c` | Tenant-scoped only; client-portal callers further scoped to their own `client_id` |
| `POST /tickets/read` | 401 | `tkt.v` | `desk_support_agent` sees `assignee_user_id == user.id`; client-portal roles see `client_id == own client` |
| `PUT /tickets/{id}/status` | 401 | `tkt.u` | Same scoping as `tkt.v` for non-admins |
| `PUT /payments/{id}/verify` | 401 | `pay.u` | `desk_admin`/`desk_support_lead` only in practice |
| `GET`/`PUT /company-settings` | 401 | `cfg.v`/`cfg.u` | `desk_admin` only |

Every controller method carries `@RequiresPermission(module =
"manage-my-desk", feature = <id>, action = <letter>)`
([Rule 4](IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level)).
ABAC row scoping is evaluated in the service layer via
`accessService.requireAbac(...)` / `accessService.rowFilter(...)`.

### Coding Standards (this feature)

**Java:** Domain entities (`Ticket`, `ClientAccount`, `SlaPolicy`, ...)
are immutable `record`s. All SQL in
`apps/manage-my-desk/db/commands/`. No business logic in controllers —
delegate to `*Service`. Constants in `ManageMyDeskConstants.java` only.
Monetary fields use `BigDecimal` end to end — never `double`/`float`.

**Flutter:** `ManageMyDeskConstants` class in
`apps/manage-my-desk/mobile/lib/constants/`. All
`TextEditingController` instances disposed in `dispose()`.

**TypeScript/React:** `SEVERITY_OPTIONS`, `PAYMENT_MODE_OPTIONS`, and
`DESK_API_BASE` in `manageMyDeskConstants.ts`. `LookupField` and
`DataTable` imported from `common/frontend` — not re-implemented.

### Directory Confirmation

```
apps/manage-my-desk/
    backend/          ← all controller + service + repository Java
    frontend/          ← pages using common field primitives
    mobile/            ← Flutter pages using common widgets
    forms/             ← catalog defaults compiled into FormEnvelope
    db/schema/         ← 18 schema YAML files (this drop), database: OPZDESK
    db/commands/       ← all SQL named commands (implementation)
common/frontend/src/
    theme/tokens.ts    ← reused, unchanged
```
