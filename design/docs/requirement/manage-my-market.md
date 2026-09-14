# Requirement: Manage My Market

**App:** `manage-my-market` (renamed from `manage-my-marketing` — `apps/manage-my-market`)
**DB target:** `opzmarket` (per-company, own logical database — [doc 28](../architecture/28-per-application-database-design.md))
**Access level:** Role-based per feature (see RBAC in DB section) — no single fixed role
**Architecture ref:** [doc 30](../architecture/30-market-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md) · [doc 26 §2](../architecture/26-rbac-db-design.md) · [doc 22](../architecture/22-common-fields-forms-fk.md)
**Reference source:** `refer_mmo/opz-market` (requirements extracted only — no code, package, or table name reused)

---

## What It Does

A lead generation and marketing operations core: leads move through
capture → assignment → follow-up → conversion, marketing campaigns
(multi-channel, templated) and automated nurture journeys run against
them, a telemarketing dialer queue drives agent calling, and a referral
program tracks partners and payouts. Matches the app catalog's own
description: "Plan, manage, and track marketing activities."

This is a **scoped-down** first design of a much larger reference app —
see [doc 30 §1](../architecture/30-market-domain-design.md#1-what-the-reference-app-actually-is--and-why-this-drop-is-scoped-narrower)
for why SEO monitoring and social media management (both real, wired
features in the reference, not mockups) are deferred to a future phase
rather than compressed into this drop, and
[doc 30 §2](../architecture/30-market-domain-design.md#2-domain-model)
for the consolidations applied over the reference (four overlapping lead
shapes → one `mkt_lead`; two duplicate assignment-history tables → one;
a `User` entity mixing login credentials with a full HR profile → split
between `modules/identity` and an optional link to `apps/manage-my-
people`).

This app owns no identity or RBAC data of its own, and no HR-profile data
either. Every person reference (lead owner, campaign creator, call agent,
follow-up assignee) is a cross-database logical link to
`modules/identity`'s `id_user`; an agent's department/designation/manager
optionally links to `apps/manage-my-people`'s `ppl_person` instead of
being re-entered here. See Dependencies and
[doc 30 §2.5](../architecture/30-market-domain-design.md#25-no-local-identity-no-duplicated-hr-profile).

**Rename:** `modules/apps/db/seed/application_catalog.yaml`'s
`app_key: manage-my-marketing` (`opz-004`) is renamed to
`manage-my-market`, and `apps/manage-my-marketing/` renamed to
`apps/manage-my-market/`, as an explicit part of this drop (not a design
decision — a direct instruction).

**Design-only drop:** per instruction, this requirement and its
architecture doc are design artifacts. The **only** files actually created
in this drop are the DB schema YAML files under
[`apps/manage-my-market/db/schema/`](../../../apps/manage-my-market/db/schema/).
Everything else in "Files to Create" below is implementation for later.

---

## Entities & Tables

All tables are in `opzmarket` — this app's **own** logical database, per
[doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application),
not the shared `opzmain`. `company_id`, every `*_user_id` column, and the
one optional `linked_person_id` are therefore **cross-database** (to
`opzmain`, `opzuser`, and optionally `opzpeople`), enforced at the
application layer per
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
never a Postgres `FOREIGN KEY`. Full column definitions, indexes, and
foreign keys are the schema YAML files under
`apps/manage-my-market/db/schema/` — this section is the human-readable
summary.

### Lead / CRM core

| Table | Purpose |
|---|---|
| `mkt_lead` | Unified lead record — `lead_source_type` (`MANUAL`\|`INBOX`\|`REFERRAL`\|`CAMPAIGN`\|reserved `SOCIAL_MENTION`), contact fields, `status`, `priority`, `estimated_value`, `owner_user_id` |
| `mkt_lead_assignment` | Ownership-change history (`assigned_by_user_id`, `assigned_to_user_id`, `transfer_reason`) |
| `mkt_lead_activity` | Timeline: notes, status/assignment changes, links to a call or follow-up |
| `mkt_lead_score` | 1:1 computed engagement score + `breakdown_json` |
| `mkt_lead_followup` | Scheduled callback task, referencing `lead_id` only (not a copy of the lead's contact fields) |
| `mkt_lead_code_sequence` | Generates `mkt_lead.lead_code` |

### Campaign & journey automation

| Table | Purpose |
|---|---|
| `mkt_campaign` | `campaign_name`, `status`, `start_date`/`end_date`, `budget` |
| `mkt_campaign_channel` | Per-channel budget/spend within a campaign |
| `mkt_campaign_recipient` | A lead's delivery status within one campaign |
| `mkt_email_template` | Reusable subject/body content |
| `mkt_audience` | Saved lead segment (`rules_json`) |
| `mkt_journey` | Automated nurture workflow (`trigger_type`, `flow_definition_json`) |
| `mkt_journey_enrollment` | A lead's live position within a journey |

### Telemarketing

| Table | Purpose |
|---|---|
| `mkt_call_queue` | A dialer queue |
| `mkt_call_queue_item` | A lead's position/state within a queue |
| `mkt_call_log` | A completed call (`recording_url`, never inline audio) |

### Referral marketing

| Table | Purpose |
|---|---|
| `mkt_referrer` | A referral partner — `leads_count`/`rewards_paid_total`/`rewards_pending_total` are maintained aggregates |
| `mkt_referral_reward` | A payout owed/paid against a converted referral lead |

### Agent profile

| Table | Purpose |
|---|---|
| `mkt_agent_profile` | `agent_user_id` (PK, cross-app `id_user`), `marketing_type`, optional `linked_person_id` (cross-app `ppl_person`) |

---

## API Endpoints

Base path: `/api/v1/opzhub/manage-my-market`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/leads` | Create a lead |
| `POST` | `/leads/read` | List leads (paginated, filterable by `status`, `lead_source_type`, `owner_user_id`) or read by `id` |
| `PUT` | `/leads` | Update lead details |
| `DELETE` | `/leads` | Disqualify a lead (`status → DISQUALIFIED`) — not a hard delete while activity/assignments exist |
| `GET` | `/leads/code/preview` | Preview next `lead_code` for a prefix |
| `POST` | `/leads/code/reserve` | Reserve and return the next `lead_code` |
| `PUT` | `/leads/{id}/assign` | Reassign owner; writes a `mkt_lead_assignment` row |
| `POST` | `/leads/{id}/activity` / `.../read` | Add / list timeline entries |
| `POST` | `/leads/{id}/followups` / `.../read` / `PUT` | Follow-up CRUD |
| `POST` | `/campaigns` / `.../read` / `PUT` / `DELETE` | Campaign CRUD |
| `POST` | `/campaigns/{id}/channels` / `.../read` | Channel allocation CRUD |
| `POST` | `/campaigns/{id}/recipients` / `.../read` | Recipient enrollment + delivery status |
| `POST` | `/email-templates` / `.../read` / `PUT` / `DELETE` | Template CRUD |
| `POST` | `/audiences` / `.../read` / `PUT` / `DELETE` | Segment CRUD |
| `POST` | `/journeys` / `.../read` / `PUT` / `DELETE` | Journey CRUD |
| `POST` | `/journeys/{id}/enroll` / `.../read` | Enrollment CRUD |
| `POST` | `/call-queues` / `.../read` / `PUT` / `DELETE` | Queue CRUD |
| `POST` | `/call-queues/{id}/items` / `.../read` / `PUT` | Queue item CRUD |
| `POST` | `/calls` / `.../read` | Call log create + list |
| `POST` | `/referrers` / `.../read` / `PUT` | Referrer CRUD |
| `POST` | `/referrers/{id}/rewards` / `.../read` / `PUT` | Reward CRUD |
| `GET`/`PUT` | `/agent-profiles/{userId}` | Agent profile read/update (self or admin) |

All responses use the standard `ApiEnvelope<T>` wrapper with
`correlation_id`.

---

## SQL Commands

Named SQL files in `apps/manage-my-market/db/commands/`:

```
lead.insert.sql / .find_by_id.sql / .find_by_company_and_code.sql / .list_paged.sql / .update.sql / .disqualify.sql
lead_code_sequence.reserve_next.sql   -- single UPDATE ... RETURNING, no race
lead_assignment.insert.sql / .list_by_lead.sql
lead_activity.insert.sql / .list_by_lead_paged.sql
lead_score.upsert.sql
lead_followup.insert.sql / .update.sql / .list_by_lead.sql / .list_due.sql
campaign.insert.sql / .update.sql / .list_paged.sql
campaign_channel.insert.sql / .list_by_campaign.sql
campaign_recipient.insert.sql / .update_status.sql / .list_by_campaign.sql
email_template.insert.sql / .update.sql / .list_by_company.sql
audience.insert.sql / .update.sql / .list_by_company.sql
journey.insert.sql / .update.sql / .list_by_company.sql
journey_enrollment.insert.sql / .update_node.sql / .list_by_journey.sql
call_queue.insert.sql / .list_by_company.sql
call_queue_item.insert.sql / .update.sql / .list_by_queue.sql
call_log.insert.sql / .list_by_lead.sql
referrer.insert.sql / .update.sql / .list_by_company.sql
referrer.recompute_aggregates.sql     -- recount leads/rewards for one referrer_id
referral_reward.insert.sql / .update_status.sql / .list_by_referrer.sql
agent_profile.upsert.sql / .find_by_user_id.sql
```

`lead_code_sequence.reserve_next.sql` must be a single atomic
`UPDATE ... RETURNING` (`ON CONFLICT` insert-on-missing) — never a
`SELECT` then `UPDATE`, same requirement as the other three apps' code
sequences.

---

## Files to Create

```
apps/manage-my-market/
├── module.yaml                                              ← NEW (design only, not created this drop)
├── backend/src/main/java/com/managemyopz/apps/managemymarket/
│   ├── ManageMyMarketAutoConfiguration.java                 ← NEW
│   ├── api/
│   │   ├── LeadController.java
│   │   ├── LeadFollowupController.java
│   │   ├── CampaignController.java
│   │   ├── EmailTemplateController.java
│   │   ├── AudienceController.java
│   │   ├── JourneyController.java
│   │   ├── CallQueueController.java
│   │   ├── ReferrerController.java
│   │   ├── AgentProfileController.java
│   │   └── dto/                                              (Create/Update/Response DTOs per entity)
│   ├── application/
│   │   ├── LeadService.java
│   │   ├── LeadAssignmentService.java
│   │   ├── LeadCodeSequenceService.java
│   │   ├── LeadActivityService.java
│   │   ├── LeadScoreService.java
│   │   ├── LeadFollowupService.java
│   │   ├── CampaignService.java
│   │   ├── JourneyService.java
│   │   ├── CallQueueService.java
│   │   ├── ReferrerService.java                              (reward + aggregate maintenance)
│   │   ├── AgentProfileService.java
│   │   └── ManageMyMarketConstants.java
│   ├── domain/
│   │   ├── Lead.java
│   │   ├── Campaign.java
│   │   ├── Journey.java
│   │   └── ... (one record per entity)
│   └── data/
│       ├── LeadRepository.java / DataClientLeadRepository.java
│       └── ... (one repository pair per entity)
├── db/
│   ├── schema/                                               ← EXISTS (this drop) — 19 files, database: OPZMARKET
│   │   ├── mkt_lead.yaml
│   │   ├── mkt_lead_assignment.yaml
│   │   ├── mkt_lead_activity.yaml
│   │   ├── mkt_lead_score.yaml
│   │   ├── mkt_lead_followup.yaml
│   │   ├── mkt_lead_code_sequence.yaml
│   │   ├── mkt_campaign.yaml
│   │   ├── mkt_campaign_channel.yaml
│   │   ├── mkt_campaign_recipient.yaml
│   │   ├── mkt_email_template.yaml
│   │   ├── mkt_audience.yaml
│   │   ├── mkt_journey.yaml
│   │   ├── mkt_journey_enrollment.yaml
│   │   ├── mkt_call_queue.yaml
│   │   ├── mkt_call_queue_item.yaml
│   │   ├── mkt_call_log.yaml
│   │   ├── mkt_referrer.yaml
│   │   ├── mkt_referral_reward.yaml
│   │   └── mkt_agent_profile.yaml
│   └── commands/                                             ← NEW (see SQL Commands above)
├── forms/                                                    ← NEW (catalog defaults, doc 22 §7)
│   ├── lead.create.yaml
│   ├── lead.edit.yaml
│   ├── lead.list.yaml
│   ├── campaign.edit.yaml
│   ├── journey.edit.yaml
│   ├── call-queue-item.edit.yaml
│   ├── referrer.edit.yaml
│   └── agent-profile.edit.yaml
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   ├── menu.ts
│   ├── manageMyMarketConstants.ts
│   └── pages/
│       ├── LeadListPage.tsx
│       ├── LeadDetailPage.tsx                                (tabs: activity, followups, assignment history)
│       ├── CampaignListPage.tsx
│       ├── CampaignDetailPage.tsx                             (tabs: channels, recipients)
│       ├── JourneyBuilderPage.tsx
│       ├── CallQueuePage.tsx                                  (dialer-style working view)
│       ├── ReferrerListPage.tsx
│       └── AgentProfilePage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        ├── lead_list_page.dart
        └── call_queue_page.dart
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| `lead_code` unique per company, generated (not user-typed) | `lead_code_sequence.reserve_next.sql`; DB unique index as final guard |
| Reassigning a lead's owner always writes a `mkt_lead_assignment` row | `LeadAssignmentService` — the `PUT /leads/{id}/assign` endpoint never updates `owner_user_id` directly without it |
| Disqualifying a lead is always `status = DISQUALIFIED`, never a physical `DELETE` | `LeadService.disqualify()` — the `DELETE /leads` endpoint calls this |
| `mkt_referrer`'s aggregate columns are never client-writable | Excluded from `ReferrerUpdateRequest`; only `ReferrerService` recomputes them, in the same transaction as a lead conversion or reward status change |
| A `mkt_referral_reward` requires its `referrer_id` to exist and belong to the same company as the reward | Service-layer check before write |
| `mkt_call_queue_item.call_attempts` increments only from a real `mkt_call_log` insert against the same lead, never a direct client field write | `CallQueueService` increments it inside the same transaction as `CallLogService.insert()` |
| Every table's `company_id`, every `*_user_id`, and `mkt_agent_profile.linked_person_id` are validated against the owning database before write, never assumed present | Service-layer existence check per [doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication), same `Conflict`/`err: fk` shape as a same-database FK |

---

## Dependencies

- `identity` module — session, RBAC tables (`id_role`, `id_user_role`,
  `id_role_permission`, `id_user_permission`, `id_field_definition` from
  [doc 26](../architecture/26-rbac-db-design.md)), and a registered named
  command this app calls cross-database (`identity.find_user_by_id`,
  `opzuser`) to resolve agent display names. **No schema change to
  `identity` is required.**
- `company-setup` — `company_information` (`opzmain`) must exist for the
  cross-database `company_id` reference on every table in this app.
- **Optional:** `apps/manage-my-people` — if installed,
  `mkt_agent_profile.linked_person_id` may reference its `ppl_person`.
  Not required; stays `null` cleanly if that app is absent
  ([doc 22 §6.2](../architecture/22-common-fields-forms-fk.md#62-when-not-to-use-a-postgres-fk-logical-fk)).
- **New platform capability:** `OPZMARKET` as a provisioned logical
  database (connection pool, migration target, backup schedule) —
  [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).
- `modules/apps` — the catalog seed rename
  (`manage-my-marketing` → `manage-my-market`) **is performed as part of
  this drop** (explicit instruction), unlike the seed additions/changes
  noted-but-deferred in the other three requirement docs.
  `ApplicationFolderPresence` still requires
  `apps/manage-my-market/{frontend,backend,mobile}` to exist before the
  app can be licensed — not performed in this drop (implementation only).
- **Future phase, not this drop:** SEO monitoring and social media
  management ([doc 30 §1](../architecture/30-market-domain-design.md#1-what-the-reference-app-actually-is--and-why-this-drop-is-scoped-narrower),
  §4). Both need a shared external-integration credential vault that
  does not exist yet (same class of gap as `modules/mail` for SMTP/IMAP).

---

## GUI Metadata Design

Every screen renders from `id_field_definition` via `GET
/api/v1/opzhub/forms/{form_id}` ([doc 22](../architecture/22-common-fields-forms-fk.md) §3).

### Screen: Lead (`manage-my-market.lead.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `lead_source_type` | Source Type | `select` | Yes | `MANUAL`, `INBOX`, `REFERRAL`, `CAMPAIGN` (`SOCIAL_MENTION` hidden — reserved, not yet operational) | `led` `c` | Locked (readonly) after create |
| `display_name` | Name | `text` | Yes | Max 200 chars | `led` `c`/`u` | — |
| `status` | Status | `radio` | Yes | `NEW`,`CONTACTED`,`QUALIFIED`,`CONVERTED`,`LOST`,`DISQUALIFIED` | `led` `u` only (not `c`) | — |
| `referrer_id` | Referrer | `lookup` | Conditional | `fk: { res: "manage-my-market.referrer" }`; required when `lead_source_type = REFERRAL` | `led` `c`/`u` | Visible only when `lead_source_type = REFERRAL` |
| `owner_user_id` | Owner | `lookup` | No | `fk: { res: "identity.user" }` | `led` `u` | Changing this field routes through `PUT /leads/{id}/assign`, not the general update, so a `mkt_lead_assignment` row is always written |

### Screen: Call Queue Item (`manage-my-market.call-queue-item.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `status` | Status | `select` | Yes | `PENDING`,`IN_PROGRESS`,`COMPLETED`,`SKIPPED` | `cal` `u` | — |
| `priority` | Priority | `radio` | No | `HOT`,`WARM`,`COLD` | `cal` `c`/`u` | — |
| `next_call_scheduled_at` | Next Call | `datetime` | No | Future date/time only | `cal` `u` | — |

### Screen: Lead Directory (list)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `lead_code` | Code | `text` | Yes | `led` `v` |
| `display_name` | Name | `text` | Yes | `led` `v` |
| `lead_source_type` | Source | `badge` | Yes | `led` `v` |
| `status` | Status | `status-badge` | Yes | `led` `v` |
| `owner_user_id` (joined) | Owner | `text` | No | `led` `v` |
| — | Actions | `actions` | No | `led` `v` | View, Edit (`u`), Disqualify (`d`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import:
[csv]` with `ops: [c]` (bulk lead import from a trade show/list purchase
is a real, common marketing workflow — each imported row still reserves
its own `lead_code` server-side during the import commit), `bulk: { u:
true, d: false }`.

### Metadata-Driven Rules

- `owner_user_id` is never directly editable through the general lead
  update form — the UI always routes through the dedicated "Reassign"
  action so `mkt_lead_assignment` history is never skipped (same pattern
  as `manage-my-desk`'s ticket `status` never being a free-editable
  field).
- `referrer_id` visibility is conditional on `lead_source_type`, driven
  by FormEnvelope `when` ([doc 22 §4.3](../architecture/22-common-fields-forms-fk.md#43-depends-on-still-from-be)),
  not a hardcoded `if` in the component.
- Campaign/journey/audience JSON fields (`flow_definition_json`,
  `rules_json`) render through a dedicated builder UI component, never a
  raw JSON textarea — the field type registry entry for these is a
  future addition to `common/frontend/src/fields/registry.ts`
  (doc 22 §2), not invented per-page.

---

## Directory Placement

```
apps/manage-my-market/          ← Application-specific (sold, licensed app)
│                                   Own logical database: OPZMARKET (doc 28 §2)
├── backend/
├── frontend/
│   └── pages/                  ← Uses field primitives from common/frontend, not its own copies
├── mobile/
├── forms/                      ← Catalog defaults; runtime = id_field_definition (doc 26) + FormEnvelope (doc 22)
└── db/
    ├── schema/                 ← EXISTS (this drop) — 19 files, database: OPZMARKET
    └── commands/

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reused for referrer_id, owner_user_id, assigned_agent_user_id
│   └── ... (no new control types needed for the core fields — this app introduces no field kind not already in doc 22 §2)
├── theme/tokens.ts               ← Reused; no new colors
└── icons/                        ← "marketing" icon_key already exists in the catalog; no new icon needed for the rename
```

No changes to `modules/identity/` are required — see Dependencies above.
`modules/apps/db/seed/application_catalog.yaml` **is** changed (the
rename), as covered above.

**Rules:**
- No module-local reimplementation of `LookupField`, `DataTable`, or any
  field control ([doc 22](../architecture/22-common-fields-forms-fk.md) §10).
- `mkt_referrer` lookups are reused by every screen that needs a
  referrer reference via the kernel `LookupField` +
  `/lookup/{res}` API.

---

## Constants

### Backend (`apps/manage-my-market/backend/.../ManageMyMarketConstants.java`)

```java
public static final String LEAD_SOURCE_MANUAL          = "MANUAL";
public static final String LEAD_SOURCE_INBOX            = "INBOX";
public static final String LEAD_SOURCE_REFERRAL         = "REFERRAL";
public static final String LEAD_SOURCE_CAMPAIGN         = "CAMPAIGN";
public static final String LEAD_SOURCE_SOCIAL_MENTION   = "SOCIAL_MENTION"; // reserved, phase 2

public static final String LEAD_STATUS_NEW               = "NEW";
public static final String LEAD_STATUS_CONTACTED         = "CONTACTED";
public static final String LEAD_STATUS_QUALIFIED         = "QUALIFIED";
public static final String LEAD_STATUS_CONVERTED         = "CONVERTED";
public static final String LEAD_STATUS_LOST               = "LOST";
public static final String LEAD_STATUS_DISQUALIFIED      = "DISQUALIFIED";

public static final String PRIORITY_HOT                  = "HOT";
public static final String PRIORITY_WARM                 = "WARM";
public static final String PRIORITY_COLD                 = "COLD";

public static final int    LEAD_CODE_MAX_LEN              = 32;
```

### Frontend (`apps/manage-my-market/frontend/manageMyMarketConstants.ts`)

```typescript
export const MARKET_API_BASE           = "/api/v1/opzhub/manage-my-market";
export const LEAD_LIST_HEADING         = "Leads";
export const LEAD_SOURCE_OPTIONS = [
  { value: "MANUAL",   label: "Manual"   },
  { value: "INBOX",    label: "Inbox"    },
  { value: "REFERRAL", label: "Referral" },
  { value: "CAMPAIGN", label: "Campaign" },
];
export const LEAD_STATUS_OPTIONS = [
  { value: "NEW",          label: "New"          },
  { value: "CONTACTED",    label: "Contacted"    },
  { value: "QUALIFIED",    label: "Qualified"    },
  { value: "CONVERTED",    label: "Converted"    },
  { value: "LOST",         label: "Lost"         },
  { value: "DISQUALIFIED", label: "Disqualified" },
];
```

Colors, spacing, and icon keys stay in `common/frontend/src/theme/tokens.ts`
and `common/frontend/src/icons/` — not duplicated here
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).

---

## Optimization, Performance & Memory

See [doc 30 §6](../architecture/30-market-domain-design.md#6-performance-optimization-memory-domain-specific-notes)
for the domain-specific reasoning. Summary of concrete rules:

### Performance
- `mkt_referrer`'s aggregate columns avoid a live `SUM`/`COUNT` per row
  on the referral leaderboard.
- Lead/campaign/call-queue list screens are always server-paginated with
  filters pushed into their `*.list_paged.sql`.
- `lead_code_sequence.reserve_next.sql` is one atomic
  `UPDATE ... RETURNING` — no read-then-write race, including during
  bulk import.
- Campaign send / journey enrollment fan-out is batched through
  `BrokerClient`, never a per-lead HTTP round trip.

### Memory
- **React:** `LeadDetailPage` tabs (activity, follow-ups, assignment
  history) fetch their own data lazily on tab-select — same pattern as
  `apps/manage-my-people`'s `PersonProfilePage` and `apps/manage-my-desk`'s
  `TicketDetailPage`.
- **Java:** `LeadActivityService` streams activity pages
  (`lead_activity.list_by_lead_paged.sql`) — never loads a lead's full
  history into one list.
- Call recordings are never loaded as row payload — only `recording_url`
  is selected; audio streams from object storage on direct request.

### Optimization
- Bulk lead import validates and reserves `lead_code` per row inside one
  server-side commit — never a client-side loop of individual
  `POST /leads` calls.
- Bulk update on the lead directory touches only `mode: edit` fields per
  doc 22 §10.2 — never a blind full-row rewrite.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> Market domain design: [doc 30](../architecture/30-market-domain-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-market/` (mirroring
the `01-unit/modules/<name>/` convention from
[Rule 1](IMPLEMENTATION_RULES.md#rule-1--unit-test-cases-separate-repo),
extended for the `apps/` vs `modules/` split, same as the other three
`apps/manage-my-*` requirement docs). No test files under
`apps/manage-my-market/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `LeadServiceTest` | Create validation; disqualify-not-delete; `lead_code` reservation atomicity |
| `LeadAssignmentServiceTest` | Every reassignment writes a `mkt_lead_assignment` row; `owner_user_id` never changes without one |
| `ReferrerServiceTest` | Aggregate columns recompute correctly on lead conversion and reward status change; never client-writable |
| `CallQueueServiceTest` | `call_attempts` increments only from a real call log insert |
| `LeadControllerTest` | RBAC annotation enforcement per feature id; ABAC owner/agent row scoping |

### RBAC in DB

Feature ids: `led`, `cmp`, `jny`, `cal`, `ref`, `agt` (full table and
rationale: [doc 30 §5](../architecture/30-market-domain-design.md#5-rbac--abac)).

Starter roles seeded by this app's migration:

```sql
INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('market_admin',        'Marketing Administrator', true),
  ('market_manager',      'Campaign Manager',        true),
  ('market_telecaller',   'Telemarketing Agent',     true),
  ('market_field_agent',  'Field Marketing Agent',   true);

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('market_admin',       'manage-my-market', 'led', 'vcud'),
  ('market_admin',       'manage-my-market', 'cmp', 'vcud'),
  ('market_admin',       'manage-my-market', 'jny', 'vcud'),
  ('market_admin',       'manage-my-market', 'cal', 'vcud'),
  ('market_admin',       'manage-my-market', 'ref', 'vcud'),
  ('market_admin',       'manage-my-market', 'agt', 'vcud'),
  ('market_manager',     'manage-my-market', 'led', 'vcu'),
  ('market_manager',     'manage-my-market', 'cmp', 'vcu'),
  ('market_manager',     'manage-my-market', 'jny', 'vcu'),
  ('market_manager',     'manage-my-market', 'cal', 'v'),
  ('market_manager',     'manage-my-market', 'ref', 'v'),
  ('market_telecaller',  'manage-my-market', 'led', 'vu'),
  ('market_telecaller',  'manage-my-market', 'cal', 'vcu'),
  ('market_telecaller',  'manage-my-market', 'agt', 'v'),
  ('market_field_agent', 'manage-my-market', 'led', 'vu'),
  ('market_field_agent', 'manage-my-market', 'agt', 'v');
```

`market_telecaller`'s `led.u` and `cal.*` are further narrowed by ABAC to
`resource.owner_user_id == user.id` / `resource.assigned_agent_user_id ==
user.id` respectively. Every role's `agt.v` (self) is narrowed to
`resource.agent_user_id == user.id`; only `market_admin` holds unscoped
`agt.*`. Company admins may layer `id_user_permission` GRANT/REVOKE rows
on top of these four starter roles without creating new roles, per
[Rule 2](IMPLEMENTATION_RULES.md#rule-2--rbac-in-db-with-optimized-tables).

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `manage-my-market.lead.create` | Create Lead |
| `manage-my-market.lead.edit` | Edit Lead |
| `manage-my-market.lead.list` | Lead Directory |
| `manage-my-market.campaign.edit` | Campaign Editor |
| `manage-my-market.journey.edit` | Journey Builder |
| `manage-my-market.call-queue-item.edit` | Call Queue Working View |
| `manage-my-market.referrer.edit` | Referrer Admin |
| `manage-my-market.agent-profile.edit` | Agent Profile |

Example migration row (`lead_source_type` field, DB-driven allowed
values):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, display_order, allowed_values, subactions
) VALUES (
  'manage-my-market.lead.create', 'lead_source_type', 'Source Type', 'select',
  true, 1,
  '[{"value":"MANUAL","label":"Manual"},{"value":"INBOX","label":"Inbox"},
    {"value":"REFERRAL","label":"Referral"},{"value":"CAMPAIGN","label":"Campaign"}]'::jsonb,
  '[{"action":"lock_after_create","trigger":"submit"}]'::jsonb
);
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /leads` | `SessionAuthFilter` (401) | `led.c` | Tenant-scoped only |
| `POST /leads/read` | 401 | `led.v` | Non-admins see rows where they are `owner_user_id`; admins/managers unscoped |
| `PUT /leads/{id}/assign` | 401 | `led.u` | `market_manager`/`market_admin` only in practice (no telecaller grant on reassignment) |
| `DELETE /leads` | 401 | `led.d` | `market_admin` only in practice |
| `PUT /call-queues/{id}/items/{itemId}` | 401 | `cal.u` | `resource.assigned_agent_user_id == user.id` for `market_telecaller` |
| `GET`/`PUT /agent-profiles/{userId}` | 401 | `agt.v`/`agt.u` | `userId == user.id` unless caller holds unscoped `agt.*` (`market_admin`) |

Every controller method carries `@RequiresPermission(module =
"manage-my-market", feature = <id>, action = <letter>)`
([Rule 4](IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level)).
ABAC row scoping is evaluated in the service layer via
`accessService.requireAbac(...)` / `accessService.rowFilter(...)`.

### Coding Standards (this feature)

**Java:** Domain entities (`Lead`, `Campaign`, `Journey`, ...) are
immutable `record`s. All SQL in
`apps/manage-my-market/db/commands/`. No business logic in controllers —
delegate to `*Service`. Constants in `ManageMyMarketConstants.java` only.

**Flutter:** `ManageMyMarketConstants` class in
`apps/manage-my-market/mobile/lib/constants/`. All
`TextEditingController` instances disposed in `dispose()`.

**TypeScript/React:** `LEAD_SOURCE_OPTIONS`, `LEAD_STATUS_OPTIONS`, and
`MARKET_API_BASE` in `manageMyMarketConstants.ts`. `LookupField` and
`DataTable` imported from `common/frontend` — not re-implemented.

### Directory Confirmation

```
apps/manage-my-market/
    backend/          ← all controller + service + repository Java
    frontend/          ← pages using common field primitives
    mobile/            ← Flutter pages using common widgets
    forms/             ← catalog defaults compiled into FormEnvelope
    db/schema/         ← 19 schema YAML files (this drop), database: OPZMARKET
    db/commands/       ← all SQL named commands (implementation)
common/frontend/src/
    theme/tokens.ts    ← reused, unchanged
modules/apps/db/seed/application_catalog.yaml  ← renamed (this drop)
```
