# 31 — Finance / Billing Domain Design (`apps/manage-my-finance`)

This document is **design only** — see [design/docs/requirement/manage-my-finance.md](../requirement/manage-my-finance.md)
for the implementation-ready requirement, and [apps/manage-my-finance/db/schema/](../../../apps/manage-my-finance/db/schema/)
for the DB schema YAML files (the one exception to "design only" in this drop).
This app's tables live in their own logical database, `OPZFINANCE`, per
[doc 28](28-per-application-database-design.md) — every `company_id →
company_information` reference and every `*_user_id → id_user` reference
below is **cross-database**, enforced at the application layer, never a
Postgres `FOREIGN KEY`.

Reference source: `refer_mmo/opz-finance` (an existing, not-cleanly-
structured reference) was read to extract *requirements*, not copied. No
code, package name, or table name from that app is reused as-is.

## 1. Two reference codebases under one folder, only one real

`opz-finance` contains **two entirely separate backends**:

- `finance-platform/backend/` — module names (`module-account-twin`,
  `module-bills`, `module-budget`, `module-goals`, `module-insights`,
  `module-investments`, `module-transactions`, `platform-entity-dna`, ...)
  that closely mirror `opz-hrms`'s `hrms-platform` structure (`module-
  employee-twin`, `platform-org-dna`). **Every module folder is
  completely empty — zero `.java` files** except a 2-file
  `app-bootstrap`. This is pure directory scaffolding with no
  implementation at all, likely copied from the HRMS reference's
  structure and never filled in.
- `backend/` (package `com.billingplatform`) — a **336-file**, fully
  wired Spring Boot subscription-billing platform: ~70 `@Entity` model
  classes, ~85 REST controllers, matching repositories and services.

Per this session's evidence-based practice (real, wired code over folder
names — the same standard applied to `opz-desk`, `opz-data`, and
`opz-market`), this design is built from `com.billingplatform`, not the
empty `finance-platform`, despite the latter's naming suggesting closer
alignment with the catalog's one-line description ("Manage finances,
expenses, and transactions"). `com.billingplatform`'s actual domain —
issuing invoices to customers, receiving payments, tracking receivables —
is a perfectly reasonable reading of that same description: a business's
*own* billing operations, not a personal-finance tracker.

## 2. A third kind of reference-app incompleteness

This session has now seen three distinct evidence tiers across four
reference apps:

| Tier | Example | What it means for design |
|---|---|---|
| Fully implemented (fields + repository + controller) | `opz-desk`'s `Ticket`; this app's `Customer`, `Invoice` | Trustworthy field-level evidence |
| Frontend-only mockup, no backend at all | `opz-desk`'s `ServiceCatalog`/`KnowledgeBase` pages | Not a requirement — exclude or defer |
| **Wired but fieldless** (new, this app) | `PaymentTransaction`, `PaymentSource`, `Refund`, `WriteOff`, `Dispute`, `Payout`, `ARCollectionsQueue`, `PaymentReconciliation` — each a real `@Entity`/`@Table`/`JpaRepository`/`@RestController`, but the model class declares **no field beyond `id`** | Real, named intent — a working REST CRUD shell was generated — but no field-level evidence. Designed here from the entity's own name/table and standard AR/billing domain practice, not guessed arbitrarily. |

`PaymentSource` additionally duplicates the fully-fielded
`CustomerPaymentMethod` (both model "a stored payment method"; only the
latter has real columns) — the same class of same-concept duplication
already seen in `apps/manage-my-market`'s four lead shapes.

## 3. Domain model

Full column-level detail is in the requirement doc and the schema YAML
files. This section covers the generalization, consolidation, and
correctness decisions that differ from the reference.

### 3.1 `fin_customer_payment_method` replaces two tables with one

`CustomerPaymentMethod` (fully fielded: `type`, `status`, `is_primary`,
`is_backup`, `details`) and the empty-stub `PaymentSource` model the same
concept. This design keeps one table and is explicit that
`gateway_token`/`details_json` must **never** contain a raw card/bank
number or CVV — only an opaque gateway reference and display-safe
metadata (brand, last 4 digits, expiry) — enforced by
`expose_generic_api: false` and by excluding the column from any list
projection, the same discipline already applied to
`ppl_person_identifier.identifier_value` and
`ppl_person_bank_account.account_number` in `apps/manage-my-people`.

### 3.2 `fin_payment_allocation` replaces a vague "reconciliation" entity

The reference's `PaymentReconciliation` is one of the six empty-stub
entities (§2) with no field evidence and an ambiguous name. Rather than
inventing an arbitrary shape for it, this design uses the standard,
well-known billing pattern instead: a many-to-many link between payments
and invoices with an `allocated_amount` — because one payment can cover
multiple invoices, and one invoice can be paid across multiple partial
payments. `fin_invoice.amount_paid`/`amount_due` are maintained from
these rows, never client-writable, same discipline as
`dat_business.rating_average` (`apps/manage-my-data`) and
`mkt_referrer`'s aggregate columns (`apps/manage-my-market`).

### 3.3 `fin_code_sequence` — one sequence table, not three

The reference's `InvoiceSequence` (`tenantId` + `year` + `lastSequence`)
is a genuinely good design — year-scoped invoice numbering is a real
jurisdictional requirement most of this session's other code-sequence
tables didn't need. Rather than copying it once for invoices and
separately re-deriving similar tables for credit notes and customer
codes, `fin_code_sequence` adds a `sequence_type` discriminator
(`INVOICE` \| `CREDIT_NOTE` \| `CUSTOMER`) so all three numbered-document
types share one atomic counter table — the same generalize-via-
discriminator technique used throughout this session. `fiscal_year` is
`NOT NULL` with a `0` sentinel for non-year-scoped types (`CUSTOMER`)
rather than nullable, because a nullable column inside a unique index
lets Postgres treat two `NULL`s as distinct and silently allow duplicate
sequences — the same pitfall already documented on
`apps/manage-my-people`'s `ppl_person_code_sequence.yaml`.

### 3.4 `fin_tax_code` replaces a hardcoded, uncompany-scoped India tax cache

The reference's `HsnSacCache` hardcodes India's HSN/SAC GST classification
scheme (`hsnOrSacCode`, `gstRate`) as a **global cache with no company
scope at all**. `fin_tax_code` is company-managed data — a company
outside India configures VAT categories, US sales-tax codes, or HSN/SAC
if that's what applies to them — the same "company-managed taxonomy
instead of a hardcoded scheme" fix already applied to
`dat_category`/`dat_location` (`apps/manage-my-data`).
`fin_invoice_line_item.tax_rate` snapshots the rate at invoice time
(rather than always joining live) so a later rate change never rewrites
historical invoices.

### 3.5 `fin_credit_note` drops an unrelated "expense" concept

The reference's `CreditNote` carries `expenseType`/`customExpenseLabel`/
`expenseCost` — fields that model a completely different concern
(recording a business expense) bolted onto the credit-note entity. This
design leaves them out; a future business-expense feature, if designed,
gets its own table.

### 3.6 No local identity — and a known future overlap, left explicit rather than solved

Every "who" column (`created_by_user_id`, `approved_by_user_id`,
`assigned_agent_user_id`) is a cross-database logical reference to
`id_user.id` on `OPZUSER`
([doc 28 §3](28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication)) —
never a local user/role table, same fix as the other three
`apps/manage-my-*` designs.

`fin_customer` is this app's own table, not a cross-app reference to
anything. This is a deliberate, **acknowledged** scope boundary rather
than a gap papered over: `apps/manage-my-data`'s `dat_business` and
`apps/manage-my-market`'s `mkt_lead` both also model an external party a
company deals with, and none of the three reference each other. A future
CRM/sales app, or a dedicated "party" concept, may eventually warrant
reconciling these three — but adding a speculative cross-app reference
now, with no concrete consuming feature, would be exactly the kind of
premature design this session's own rules (`Rule 7`, avoid unnecessary
complexity) argue against. This is named here so it is a deliberate,
revisitable decision, not a silently duplicated concept.

## 4. GUI metadata — confirmed mechanism, no new table

Same mechanism as every other `apps/manage-my-*` design this session:
every field-level requirement is served by `id_field_definition`
([26](26-rbac-db-design.md) §2) through `FormEnvelope`
([22](22-common-fields-forms-fk.md) §3). `fin_customer_custom_field`
mirrors the EAV pattern already established in the other three apps —
definitions in `id_field_definition` under
`form_id = "manage-my-finance.customer.custom"`, values here.

## 5. Explicitly out of scope (with rationale)

| Reference sub-domain | Disposition |
|---|---|
| CPQ / Quotes (`Quote`, `QuoteLineItem`, `QuoteApproval`) | Sales-cycle feature, not billing-to-cash. Deferred — closer to a future `manage-my-sales` concern. |
| Product Catalog / Subscriptions (`ProductFamily`, `Plan`, `PlanPricePoint`, `Charge`, `Addon`, `Coupon`, `Subscription`, `SubscriptionAddon`, `SubscriptionEvent`, `ContractModification`, `UpsellRule`, `PricingExperiment`) | Subscription-commerce is a distinct, large product surface (comparable in size to this app's own core). Deferred to its own design phase, same disposition class as `apps/manage-my-market`'s SEO/social split (real, evidenced, but deserving its own pass). |
| Revenue Recognition (`PerformanceObligation`, `RevenueSchedule`, `RevrecAuditTrail`, `RevenueGoal`, `RevenueAlert`) | Specialist accounting function. This platform already reserves a distinct `ledger`/finance bounded context for general-ledger-grade functionality ([doc 00 §7](00-system-overview.md#7-bounded-contexts-feature-modules)) — `manage-my-finance` is the concrete AR/billing build, the same "concrete app vs. generic placeholder" split already established for `manage-my-desk` (vs. the `ticketing` placeholder) and `manage-my-data` (vs. `master-data`). Double-entry journal posting (`JournalEntry`) belongs to that future `ledger` app, not here. |
| Retention / Growth analytics (`AtRiskSignal`, `ChurnReason`, `WinbackCampaign`, `RetentionActivityLog`, `CustomerSegment`, `DashboardMetric`) | Customer-success/growth analytics. `CustomerSegment` in particular overlaps with `apps/manage-my-market`'s `mkt_audience` — segmentation is a marketing concern, not a billing one. Deferred to the shared `reporting` bounded context ([doc 00 §7](00-system-overview.md#7-bounded-contexts-feature-modules)), same disposition as every other app's analytics/reporting exclusion this session. |
| Multi-tenant platform admin (`Tenant`, `AccessPreset`, `TenantModuleAccess`, `IpAllowlist`, `ApiKey`, `Webhook`, `AppIntegration`) | This is the *reference app's own* SaaS-platform multi-tenancy layer — modeling "a tenant of the billing platform." It is **redundant with this platform's own** `company_information`/licensing/`modules/identity`, not a finance concern at all. Excluded entirely, not deferred. |
| Local auth/RBAC (`User`, `Role`, `Permission`, `UserRoleAssignment`, `UserToken`, `CustomUserDetails`) | Reuse `modules/identity` entirely — no local table. |
| Notifications (`OpzNotification`, `InvoiceNotification`, `NotificationDispatcherJob`, `EmailController`, `EmailTemplateController`) | Shared, cross-cutting bounded context (`notifications`, [doc 00 §7](00-system-overview.md#7-bounded-contexts-feature-modules)) — not rebuilt per app, same disposition as `apps/manage-my-desk`'s `Notification` exclusion. |
| Tax lookup service (`TaxLookupController`, live HSN/SAC API calls) | The *lookup mechanism* (calling an external tax-code API) is a future external-integration concern, same class of gap as `modules/mail` (doc 23) — `fin_tax_code` (§3.4) is the company-managed catalog it would populate, designed now; the live-lookup integration is not. |

## 6. RBAC / ABAC

Feature ids declared for `module_id = "manage-my-finance"` (≤4 chars,
[18](18-identity-rbac-abac-oauth2.md) §3.1, cap 32/app):

| Feature id | Title | Covers |
|---|---|---|
| `cus` | Customers | `fin_customer`, `fin_customer_contact`, `fin_customer_payment_method`, `fin_customer_custom_field` |
| `inv` | Invoices | `fin_invoice`, `fin_invoice_line_item`, `fin_credit_note`, `fin_credit_note_line_item` |
| `pay` | Payments | `fin_payment_transaction`, `fin_payment_allocation`, `fin_refund`, `fin_payout` |
| `wof` | Write-offs | `fin_write_off` (admin-only in practice — see RBAC in DB) |
| `dsp` | Disputes | `fin_dispute` |
| `col` | Collections | `fin_collections_queue_item` |
| `tax` | Tax codes | `fin_tax_code` (admin-only) |

Enforcement is the standard two-layer stack from
[Rule 4](../requirement/IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level).
A collections agent's `col.u` grant is narrowed by ABAC to
`resource.assigned_agent_user_id == user.id`, same manager/self
row-scoping pattern already established in
`apps/manage-my-desk`/`apps/manage-my-market`. `fin_customer_payment_method`
reads are additionally gated the same way `ppl_person_identifier` reads
are in `apps/manage-my-people`: the field-level data (`gateway_token`,
`details_json`) never appears in a list/collection response regardless
of RBAC grant. Full role list and seed rows are in the requirement doc's
"RBAC in DB" section.

## 7. Performance, optimization, memory (domain-specific notes)

Beyond the universal rules in
[Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal):

- `fin_invoice.amount_paid`/`amount_due` and `fin_credit_note.allocated_
  amount`/`available_amount` are maintained columns (§3.2) — an invoice
  list screen never runs a live `SUM` over `fin_payment_allocation` per
  row.
- `fin_code_sequence.reserve_next` (invoice/credit-note/customer number
  generation) is a single atomic `UPDATE ... RETURNING` — no
  read-then-write race, same requirement as every other app's code
  sequence this session.
- `fin_customer_payment_method` and `fin_customer_custom_field` are
  excluded from the generic CRUD API / list projections respectively —
  sensitive or high-cardinality data is never dragged through a directory
  list query.
- Invoice/customer/payment directory screens are always server-paginated
  with filters (`status`, `customer_id`, `due_date`) pushed to SQL.
- Money columns are `numeric`, never floating point — required for
  currency-correct rounding, consistent with every monetary column
  across this session's designs (`dsk_sla_policy` used integer minutes
  for the same reason: exactness over convenience).

## 8. What must not happen

- A local `fin_user`/`fin_role` table — reuse `modules/identity` (§3.6).
- Raw card/bank numbers, CVV, or full account numbers stored in
  `fin_customer_payment_method.gateway_token`/`details_json` — opaque
  gateway references and display-safe metadata only.
- A second field-metadata table for this app — use `id_field_definition`.
- A second RBAC table anywhere in this app — use the five `id_*` tables
  from doc 26.
- This app's tables declared under `database: OPZMAIN` instead of
  `OPZFINANCE`, or a Postgres `FOREIGN KEY` attempted across `OPZFINANCE`
  and `OPZMAIN`/`OPZUSER` ([doc 28](28-per-application-database-design.md)).
- `fin_invoice.amount_paid`/`amount_due` or `fin_credit_note`'s allocation
  columns written directly through the general update endpoint instead
  of derived from `fin_payment_allocation` rows.
- Re-adding `PaymentSource` as a second stored-payment-method table
  alongside `fin_customer_payment_method` (§3.1).
- A hardcoded, single-country tax scheme re-appearing instead of
  `fin_tax_code` (§3.4).
- Unit test files under `apps/manage-my-finance/**/src/test/` — they
  belong in `managemyopz-testing/01-unit/` (see requirement doc).
