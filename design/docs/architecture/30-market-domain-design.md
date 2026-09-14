# 30 — Market / Lead Generation Domain Design (`apps/manage-my-market`)

This document is **design only** — see [design/docs/requirement/manage-my-market.md](../requirement/manage-my-market.md)
for the implementation-ready requirement, and [apps/manage-my-market/db/schema/](../../../apps/manage-my-market/db/schema/)
for the DB schema YAML files (the one exception to "design only" in this drop).
This app's tables live in their own logical database, `OPZMARKET`, per
[doc 28](28-per-application-database-design.md) — every `company_id →
company_information` reference, every `*_user_id → id_user` reference,
and the one optional `linked_person_id → ppl_person` reference below are
**cross-database**, enforced at the application layer, never a Postgres
`FOREIGN KEY`.

**Rename note:** the app catalog and `apps/` folder were renamed from
`manage-my-marketing` to `manage-my-market` as part of this drop
(`modules/apps/db/seed/application_catalog.yaml`, `app_key: opz-004`) —
an explicit instruction, not a design decision documented further here.

Reference source: `refer_mmo/opz-market` (an existing, not-cleanly-
structured marketing/lead-generation suite) was read to extract
*requirements*, not copied. No code, package name, or table name from
that app is reused as-is.

## 1. What the reference app actually is — and why this drop is scoped narrower

`opz-market` is, by a wide margin, the largest and most fully realized of
the four reference apps read so far: **40 real, `@Entity`-annotated,
mostly-repository-backed** JPA classes (not frontend mockups — see
[doc 29 §1](29-data-directory-domain-design.md#1-what-the-reference-app-actually-is)
and the `manage-my-desk`/`manage-my-people` docs for what that distinction
looks like when it matters). It spans six sub-domains: lead management/
CRM, campaign & journey automation, telemarketing, referral marketing,
SEO monitoring, and social media management.

This drop designs the first four — **lead/CRM, campaign automation,
telemarketing, and referral marketing** — as one coherent, sellable
product matching the app catalog's own description ("Plan, manage, and
track marketing activities"). **SEO monitoring and social media
management are explicitly deferred**, not because they are unwired
mockups (they are real, with dedicated services —
`TechnicalSeoAuditService`, `SerpScraperService`, `SemrushAhrefsService`,
`SeoRankTrackingScheduler`, a full `SeoController`) but because:

1. Both carry a **distinct external-integration surface** — Google Ads
   OAuth, SEMrush/Ahrefs API, SERP scraping, per-platform social APIs —
   that this platform has no shared credential-vault pattern for yet
   (the same gap `modules/mail` fills for SMTP/IMAP, doc 23, not yet
   built either). Designing ~14 tables around three different unbuilt
   external-integration dependencies in one drop is speculative in a way
   the lead/campaign/telemarketing/referral core is not.
2. They are large enough, and distinct enough in operator persona
   (an SEO specialist / social media manager vs. a telemarketing agent or
   campaign manager), to deserve their own design pass rather than being
   compressed to fit this one.

Both remain real, evidenced requirements — this is a **scope split**, the
same kind `apps/manage-my-desk`'s design doc used for meetings and the AI
assistant, not an exclusion-as-non-requirement the way `apps/manage-my-
desk` treated Problem/Change (§4 has the full disposition table).

## 2. Domain model

Full column-level detail is in the requirement doc and the schema YAML
files. This section covers the generalization and correctness decisions
that differ from the reference.

### 2.1 `mkt_lead` — one record instead of four

The reference has **four** overlapping lead shapes with no shared model:
`Lead` (social-listening-derived: `userHandle`, `intentScore`), `CrmLead`
(`firstName`/`lastName`/`company`/`email`/`phone`/`status`/`source`/
`value`), `InboxLead` (omnichannel inbox: `name`/`phone`/`email`/
`priority`), and `ReferralLead` (`name`/`phone`/`email`/`referrerId`/
`value`) — plus a fifth, `LeadData`, that duplicates `CrmLead`'s contact
fields under an entirely separate table. `mkt_lead` consolidates all five
into one table with a `lead_source_type` discriminator (`MANUAL` \|
`INBOX` \| `REFERRAL` \| `CAMPAIGN` \| reserved `SOCIAL_MENTION`) — the
same generalize-via-discriminator technique used for `ppl_org_unit`
(`apps/manage-my-people`) and `dsk_category`/`dat_category`
(`apps/manage-my-desk`, `apps/manage-my-data`).

### 2.2 `mkt_lead_assignment` — one history table instead of two

The reference records the same event — a lead being reassigned — in two
near-identical tables: `LeadAssignment` (`leadId`/`assignedBy`/
`assignedTo`/`assignedAt`) and `LeadHistory` (`leadId`/`assignedBy`/
`assignedTo`/`assignedAt`/`transferReason`). `mkt_lead_assignment` is the
union of both (with `transfer_reason`).

### 2.3 `mkt_lead_followup` — reference by id, not by copy

The reference's `FollowUp` entity copies the lead's name, company, and
phone onto every follow-up row instead of joining through `leadId`.
`mkt_lead_followup.lead_id` is the only lead reference; display fields
are read from `mkt_lead`.

### 2.4 `mkt_audience.rules_json` / `mkt_journey.flow_definition_json` — jsonb, not opaque strings

The reference stores a segment's filter rules (`Audience.rulesDefinition`)
and a journey's node graph (`Journey.flowDefinition`) as plain `String`
columns — effectively serialized blobs the database cannot query into.
Both become `jsonb` here, consistent with every other structured payload
in this design set (`ppl_person_timeline.metadata_json`,
`dsk_ticket_activity.metadata_json`).

### 2.5 No local identity, no duplicated HR profile

Every "who" column (`owner_user_id`, `created_by_user_id`,
`assigned_by_user_id`/`assigned_to_user_id`, `assigned_agent_user_id`,
`agent_user_id`, `performed_by_user_id`) is a **cross-database logical
reference to `id_user.id` on `OPZUSER`**
([doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)) —
never a local `mkt_user` copy.

The reference's `User` entity goes further than the identity duplication
seen in the other three reference apps: it mixes login credentials
(`email`/`password`/`role`/`status`) **with a full HR profile**
(`department`/`designation`/`branch`/`manager`/`joiningDate`/
`workingStatus`/`experience`/`skills`) in one flat table.
`mkt_agent_profile` keeps only what is genuinely specific to this app
(`marketing_type` — telecaller, digital marketer, field agent, campaign
manager) and adds an **optional** cross-database reference,
`linked_person_id`, to `apps/manage-my-people`'s `ppl_person`
(`OPZPEOPLE`) for the HR-profile fields — the same optional-sibling-app
pattern as `dsk_group.owning_org_unit_id`
(`apps/manage-my-desk`). Department, designation, manager, and joining
date are `ppl_person`/`ppl_person_affiliation` fields when that app is
installed; they are never duplicated onto an agent's marketing profile.
`PermissionsOverride` (`empId`/`permissionsJson`) is not reproduced at
all — it is exactly what `id_user_permission`
([doc 26](26-rbac-db-design.md) §2) already provides.

### 2.6 What stayed a fixed column vs. a maintained aggregate

`mkt_referrer.leads_count` / `rewards_paid_total` / `rewards_pending_total`
are maintained aggregates, recomputed by the referral service on
lead/reward writes — same rationale and same discipline as
`dat_business.rating_average` (`apps/manage-my-data`): a directory/list
screen must not run a live aggregate query per row, and the columns are
excluded from any general update endpoint so they can't drift from a
direct client write.

## 3. GUI metadata — confirmed mechanism, no new table

Same mechanism as the other three `apps/manage-my-*` designs: every
field-level requirement is served by `id_field_definition`
([26](26-rbac-db-design.md) §2) through `FormEnvelope`
([22](22-common-fields-forms-fk.md) §3). This app introduces no EAV
custom-field table — the reference app shows no evidence of a per-
company custom-field need on leads/campaigns (unlike tickets/people/
business records in the other three apps), so none is speculatively
added; `id_field_definition` still governs every fixed field's mandatory/
format/allowed-values rules.

## 4. Explicitly out of scope (with rationale)

| Reference sub-domain | Disposition |
|---|---|
| SEO monitoring (`SeoWebsite`, `SeoTrackedKeyword`, `SeoRankHistory`, `SeoAuditLog`, `SeoBacklinkLog`, `SeoCompetitorBenchmark`, `SeoRecommendation`, `AlertRule`/`AlertHistory`, `GoogleAdsConnection`) | Real, wired, evidenced — deferred to its own design phase (§1). `GoogleAdsConnection.encryptedRefreshToken` stored per-app is also a credential-duplication anti-pattern (doc 28 §3) to fix in that future pass, not carry forward. |
| Social media management (`SocialAccount`, `SocialPost`, `SocialMediaLibrary`, `SocialInboxMessage`) | Same disposition as SEO. `SocialAccount.accessToken` stored raw in-app and `SocialMediaLibrary.url` doubling as a base64 payload column are two more anti-patterns (raw credential storage; the same base64-blob-in-DB problem `dsk_ticket_attachment` fixed) flagged for that future pass. |
| Social listening (`Keyword`, `Mention`) | Deferred with SEO/social — `mkt_lead.lead_source_type` reserves `SOCIAL_MENTION` so leads generated from social listening slot in without a schema change once it is designed. |
| `User` login/role fields | Reuse `modules/identity` entirely (§2.5). |
| `User` HR-profile fields (department, designation, branch, manager, joining date, experience, skills) | Reuse `apps/manage-my-people`'s `ppl_person`/`ppl_person_affiliation`, optionally, via `mkt_agent_profile.linked_person_id` (§2.5). |
| `PermissionsOverride` | Reuse `id_user_permission` ([doc 26](26-rbac-db-design.md) §2) — no new table. |
| `Notification` | In-app notification delivery is a shared, cross-cutting bounded context (`notifications`, [doc 00 §7](00-system-overview.md#7-bounded-contexts-feature-modules)) already named in the platform's own catalog — not rebuilt per app, same disposition `apps/manage-my-desk`'s design doc gave it. |

## 5. RBAC / ABAC

Feature ids declared for `module_id = "manage-my-market"` (≤4 chars,
[18](18-identity-rbac-abac-oauth2.md) §3.1, cap 32/app):

| Feature id | Title | Covers |
|---|---|---|
| `led` | Leads | `mkt_lead`, `mkt_lead_assignment`, `mkt_lead_activity`, `mkt_lead_score`, `mkt_lead_followup` |
| `cmp` | Campaigns | `mkt_campaign`, `mkt_campaign_channel`, `mkt_campaign_recipient`, `mkt_email_template`, `mkt_audience` |
| `jny` | Journeys | `mkt_journey`, `mkt_journey_enrollment` |
| `cal` | Telemarketing | `mkt_call_queue`, `mkt_call_queue_item`, `mkt_call_log` |
| `ref` | Referrals | `mkt_referrer`, `mkt_referral_reward` |
| `agt` | Agent profile | `mkt_agent_profile` (admin manages others'; an agent may `v`/`u` only their own) |

Enforcement is the standard two-layer stack from
[Rule 4](../requirement/IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level).
A telemarketing agent's `led.u`/`cal.*` grants are narrowed by ABAC to
`resource.owner_user_id == user.id` (leads) and
`resource.assigned_agent_user_id == user.id` (queue items) — the same
manager/self row-scoping pattern already established in
`apps/manage-my-desk` and `apps/manage-my-people`. Full role list and
seed rows are in the requirement doc's "RBAC in DB" section.

## 6. Performance, optimization, memory (domain-specific notes)

Beyond the universal rules in
[Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal):

- `mkt_referrer`'s three aggregate columns (§2.6) mean the referral
  leaderboard/list screen never runs a live `SUM`/`COUNT` per row.
- `mkt_lead_activity` is an unbounded-growth append-only child table,
  same treatment as `ppl_person_timeline`/`dsk_ticket_activity`: never
  joined on a list screen, loaded paginated only on a single lead's
  detail view.
- Campaign send/journey enrollment fan-out
  (`mkt_campaign_recipient`, `mkt_journey_enrollment` insert-per-lead) is
  a bulk operation — batched via `BrokerClient`
  ([07](07-data-cache-client-server.md) §7), never a per-lead HTTP round
  trip from the frontend.
- Lead/campaign/call-queue list screens are always server-paginated with
  filters (`status`, `lead_source_type`, `owner_user_id`) pushed to SQL.

## 7. What must not happen

- A local `mkt_user` table or a flat `role` string column — reuse
  `modules/identity` (§2.5).
- HR-profile fields (department, designation, manager, ...) duplicated
  onto `mkt_agent_profile` instead of linked to `ppl_person`.
- A second field-metadata table for this app — use `id_field_definition`.
- A second RBAC table or a `permissionsJson` blob column anywhere in this
  app — use the five `id_*` tables from doc 26.
- This app's tables declared under `database: OPZMAIN` instead of
  `OPZMARKET`, or a Postgres `FOREIGN KEY` attempted across `OPZMARKET`
  and `OPZMAIN`/`OPZUSER`/`OPZPEOPLE` ([doc 28](28-per-application-database-design.md)).
- Reintroducing any of the four separate lead shapes the reference had —
  `mkt_lead` is the one record type, `lead_source_type` is the
  discriminator.
- A future SEO/social build storing an OAuth token or API secret directly
  on an entity column, repeating the reference's
  `GoogleAdsConnection.encryptedRefreshToken` / `SocialAccount.accessToken`
  pattern — route through a shared credential vault when that phase is
  designed.
- Unit test files under `apps/manage-my-market/**/src/test/` — they
  belong in `managemyopz-testing/01-unit/` (see requirement doc).
