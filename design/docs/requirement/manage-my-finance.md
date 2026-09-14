# Requirement: Manage My Finance

**App:** `manage-my-finance` (new, `apps/manage-my-finance`)
**DB target:** `opzfinance` (per-company, own logical database — [doc 28](../architecture/28-per-application-database-design.md))
**Access level:** Role-based per feature (see RBAC in DB section) — no single fixed role
**Architecture ref:** [doc 31](../architecture/31-finance-domain-design.md) · [doc 28](../architecture/28-per-application-database-design.md) · [doc 26 §2](../architecture/26-rbac-db-design.md) · [doc 22](../architecture/22-common-fields-forms-fk.md)
**Reference source:** `refer_mmo/opz-finance` (requirements extracted only — no code, package, or table name reused)

---

## What It Does

Accounts-receivable / billing-to-cash: manage customers being billed,
issue invoices, capture payments, apply credit notes and refunds, track
overdue receivables through a collections queue, and record disputes and
payouts. Matches the app catalog's own description: "Manage finances,
expenses, and transactions."

The reference app (`opz-finance`) actually contains **two unrelated
codebases** — see
[doc 31 §1](../architecture/31-finance-domain-design.md#1-two-reference-codebases-under-one-folder-only-one-real)
for why this design is built from its 336-file `com.billingplatform`
backend (invoicing/payments/receivables), not the empty `finance-platform`
directory scaffold its folder names might otherwise suggest. It also
surfaces a third kind of reference-app incompleteness this session hadn't
seen before — real, wired entities with **zero fields** beyond an id
([doc 31 §2](../architecture/31-finance-domain-design.md#2-a-third-kind-of-reference-app-incompleteness)) —
and the fixes/consolidations applied over the reference are in
[doc 31 §3](../architecture/31-finance-domain-design.md#3-domain-model)
(one stored-payment-method table instead of two; a proper payment↔invoice
allocation table instead of a vague empty "reconciliation" entity; one
shared code-sequence table instead of three; a company-managed tax-code
catalog instead of a hardcoded, global India GST cache; an unrelated
"expense" concept removed from credit notes).

This app owns no identity or RBAC data of its own. Every person
reference (invoice creator, write-off approver, collections agent) is a
cross-database logical link to `modules/identity`'s `id_user`. The
reference app's local `User`/`Role`/`Permission` system, and its own
multi-tenant "platform admin" layer (`Tenant`, `ApiKey`, `Webhook`, ...),
are explicitly **not** reproduced — the latter is redundant with this
platform's own `company_information`/licensing, not a finance concern at
all. See Dependencies and
[doc 31 §3.6](../architecture/31-finance-domain-design.md#36-no-local-identity--and-a-known-future-overlap-left-explicit-rather-than-solved).

**Design-only drop:** per instruction, this requirement and its
architecture doc are design artifacts. The **only** files actually created
in this drop are the DB schema YAML files under
[`apps/manage-my-finance/db/schema/`](../../../apps/manage-my-finance/db/schema/).
Everything else in "Files to Create" below is implementation for later.

---

## Entities & Tables

All tables are in `opzfinance` — this app's **own** logical database,
per [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application),
not the shared `opzmain`. `company_id` and every `*_user_id` column are
therefore **cross-database** (to `opzmain` and `opzuser`), enforced at
the application layer per
[doc 28 §3](../architecture/28-per-application-database-design.md#3-rule-cross-application-data-access-without-duplication) —
never a Postgres `FOREIGN KEY`. Full column definitions, indexes, and
foreign keys are the schema YAML files under
`apps/manage-my-finance/db/schema/` — this section is the human-readable
summary.

### Customer

| Table | Purpose |
|---|---|
| `fin_customer` | The billed party — contact/billing/shipping details, tax registration number, `status` |
| `fin_customer_contact` | A named contact person at a customer |
| `fin_customer_payment_method` | Stored payment method — `gateway_token`/`details_json` never hold raw card/bank data |
| `fin_customer_custom_field` | EAV values; definitions in `id_field_definition` |

### Invoicing

| Table | Purpose |
|---|---|
| `fin_invoice` | `status`, `subtotal`/`tax_total`/`total`, maintained `amount_paid`/`amount_due` |
| `fin_invoice_line_item` | One billed line; `tax_code_id` + snapshotted `tax_rate` |
| `fin_credit_note` / `fin_credit_note_line_item` | Credit issued against a customer/invoice; maintained `allocated_amount`/`available_amount` |
| `fin_code_sequence` | Shared atomic counter for invoice/credit-note/customer numbering |
| `fin_tax_code` | Company-managed tax classification catalog (not hardcoded to one country) |

### Payments & receivables

| Table | Purpose |
|---|---|
| `fin_payment_transaction` | A payment capture attempt |
| `fin_payment_allocation` | Which invoice(s) a payment was applied to, and how much |
| `fin_refund` | Money returned to a customer against a payment |
| `fin_write_off` | An uncollectible invoice amount written off |
| `fin_dispute` | A chargeback/dispute against a payment |
| `fin_payout` | Funds settled from the gateway to the company's own bank |
| `fin_collections_queue_item` | Overdue-invoice follow-up tracking |

---

## API Endpoints

Base path: `/api/v1/opzhub/manage-my-finance`

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/customers` | Create a customer |
| `POST` | `/customers/read` | List customers (paginated, filterable by `status`) or read by `id` |
| `PUT` | `/customers` | Update customer details |
| `DELETE` | `/customers` | Archive a customer (`status → ARCHIVED`) — not a hard delete while invoices exist |
| `GET` | `/customers/code/preview` / `POST /customers/code/reserve` | Customer code generation |
| `POST` | `/customers/{id}/contacts` / `.../read` / `PUT` / `DELETE` | Contact CRUD |
| `POST` | `/customers/{id}/payment-methods` / `.../read` / `DELETE` | Payment method CRUD (create is gateway-tokenization callback only — see Business Rules) |
| `POST` | `/invoices` | Create a draft invoice |
| `POST` | `/invoices/read` | List invoices (paginated, filterable by `status`, `customer_id`, `due_date`) or read by `id` |
| `PUT` | `/invoices` | Update a draft invoice |
| `PUT` | `/invoices/{id}/issue` | Transition `DRAFT → OPEN`; locks line items |
| `PUT` | `/invoices/{id}/void` | Transition to `VOID` |
| `GET` | `/invoices/code/preview` / `POST /invoices/code/reserve` | Invoice number generation |
| `POST` | `/credit-notes` / `.../read` / `PUT /credit-notes/{id}/issue` | Credit note create/list/issue |
| `POST` | `/payments` | Record a payment transaction |
| `POST` | `/payments/{id}/allocate` | Allocate a payment across one or more invoices |
| `POST` | `/payments/read` | List payment transactions |
| `POST` | `/refunds` / `.../read` / `PUT /refunds/{id}/process` | Refund create/list/process |
| `POST` | `/write-offs` | Create a write-off (admin-approval gated — see RBAC) |
| `POST` | `/disputes` / `.../read` / `PUT /disputes/{id}/resolve` | Dispute create/list/resolve |
| `POST` | `/payouts` / `.../read` | Payout create/list |
| `POST` | `/collections-queue` / `.../read` / `PUT` | Collections queue item CRUD |
| `POST` | `/tax-codes` / `.../read` / `PUT` / `DELETE` | Tax code catalog CRUD (admin) |

All responses use the standard `ApiEnvelope<T>` wrapper with
`correlation_id`.

---

## SQL Commands

Named SQL files in `apps/manage-my-finance/db/commands/`:

```
customer.insert.sql / .find_by_id.sql / .find_by_company_and_code.sql / .list_paged.sql / .update.sql / .archive.sql
code_sequence.reserve_next.sql        -- single UPDATE ... RETURNING, no race (shared: INVOICE | CREDIT_NOTE | CUSTOMER)
customer_contact.insert.sql / .list_by_customer.sql
customer_payment_method.insert.sql / .list_by_customer.sql / .delete.sql
invoice.insert.sql / .update.sql / .issue.sql / .void.sql / .list_paged.sql / .find_by_id.sql
invoice_line_item.insert.sql / .list_by_invoice.sql
credit_note.insert.sql / .issue.sql / .list_paged.sql
credit_note_line_item.insert.sql / .list_by_credit_note.sql
payment_transaction.insert.sql / .list_paged.sql
payment_allocation.insert.sql / .list_by_invoice.sql / .list_by_payment.sql
invoice.recompute_paid_amounts.sql    -- SUM(fin_payment_allocation) for one invoice_id, runs inside the allocation transaction
refund.insert.sql / .process.sql / .list_by_customer.sql
write_off.insert.sql
dispute.insert.sql / .resolve.sql
payout.insert.sql / .list_by_company.sql
collections_queue_item.insert.sql / .update.sql / .list_paged.sql
tax_code.insert.sql / .update.sql / .list_by_company.sql
```

`code_sequence.reserve_next.sql` must be a single atomic
`UPDATE ... RETURNING` (`ON CONFLICT` insert-on-missing) — never a
`SELECT` then `UPDATE`, same requirement as every other app's code
sequence this session.

---

## Files to Create

```
apps/manage-my-finance/
├── module.yaml                                              ← NEW (design only, not created this drop)
├── backend/src/main/java/com/managemyopz/apps/managemyfinance/
│   ├── ManageMyFinanceAutoConfiguration.java                ← NEW
│   ├── api/
│   │   ├── CustomerController.java
│   │   ├── InvoiceController.java
│   │   ├── CreditNoteController.java
│   │   ├── PaymentController.java
│   │   ├── RefundController.java
│   │   ├── WriteOffController.java
│   │   ├── DisputeController.java
│   │   ├── PayoutController.java
│   │   ├── CollectionsQueueController.java
│   │   ├── TaxCodeController.java
│   │   └── dto/                                              (Create/Update/Response DTOs per entity)
│   ├── application/
│   │   ├── CustomerService.java
│   │   ├── CodeSequenceService.java
│   │   ├── InvoiceService.java                               (issue/void transitions; amount_paid/amount_due maintenance)
│   │   ├── CreditNoteService.java
│   │   ├── PaymentService.java                                (allocation logic)
│   │   ├── RefundService.java
│   │   ├── WriteOffService.java
│   │   ├── DisputeService.java
│   │   ├── PayoutService.java
│   │   ├── CollectionsQueueService.java
│   │   ├── TaxCodeService.java
│   │   └── ManageMyFinanceConstants.java
│   ├── domain/
│   │   ├── Customer.java
│   │   ├── Invoice.java
│   │   ├── PaymentTransaction.java
│   │   └── ... (one record per entity)
│   └── data/
│       ├── CustomerRepository.java / DataClientCustomerRepository.java
│       └── ... (one repository pair per entity)
├── db/
│   ├── schema/                                               ← EXISTS (this drop) — 17 files, database: OPZFINANCE
│   │   ├── fin_customer.yaml
│   │   ├── fin_customer_contact.yaml
│   │   ├── fin_customer_payment_method.yaml
│   │   ├── fin_customer_custom_field.yaml
│   │   ├── fin_code_sequence.yaml
│   │   ├── fin_invoice.yaml
│   │   ├── fin_invoice_line_item.yaml
│   │   ├── fin_tax_code.yaml
│   │   ├── fin_credit_note.yaml
│   │   ├── fin_credit_note_line_item.yaml
│   │   ├── fin_payment_transaction.yaml
│   │   ├── fin_payment_allocation.yaml
│   │   ├── fin_refund.yaml
│   │   ├── fin_write_off.yaml
│   │   ├── fin_dispute.yaml
│   │   ├── fin_payout.yaml
│   │   └── fin_collections_queue_item.yaml
│   └── commands/                                             ← NEW (see SQL Commands above)
├── forms/                                                    ← NEW (catalog defaults, doc 22 §7)
│   ├── customer.create.yaml
│   ├── customer.edit.yaml
│   ├── customer.custom.yaml
│   ├── invoice.create.yaml
│   ├── invoice.edit.yaml
│   ├── invoice.list.yaml
│   ├── credit-note.edit.yaml
│   ├── payment-transaction.edit.yaml
│   ├── collections-queue-item.edit.yaml
│   └── tax-code.edit.yaml
├── frontend/
│   ├── index.ts
│   ├── routes.tsx
│   ├── menu.ts
│   ├── manageMyFinanceConstants.ts
│   └── pages/
│       ├── CustomerListPage.tsx
│       ├── CustomerDetailPage.tsx                            (tabs: contacts, payment methods, invoices)
│       ├── InvoiceListPage.tsx
│       ├── InvoiceDetailPage.tsx                              (tabs: line items, payments, credit notes)
│       ├── PaymentListPage.tsx
│       ├── CollectionsQueuePage.tsx
│       └── TaxCodeAdminPage.tsx
└── mobile/
    ├── plugin.dart
    └── pages/
        ├── invoice_list_page.dart
        └── customer_list_page.dart
```

---

## Business Rules

| Rule | Enforcement |
|------|------------|
| `invoice_number`/`credit_note_number`/`customer_code` are unique per company, generated (not user-typed) | `code_sequence.reserve_next.sql`; DB unique index as final guard |
| An invoice's line items are locked once `status` moves past `DRAFT` | `InvoiceService.issue()` — `PUT /invoices` on a non-`DRAFT` invoice rejects line-item changes |
| Archiving a customer is always `status = ARCHIVED`, never a physical `DELETE` | `CustomerService.archive()` — the `DELETE /customers` endpoint calls this; rejected outright while open invoices exist |
| `fin_invoice.amount_paid`/`amount_due` and `fin_credit_note.allocated_amount`/`available_amount` are never client-writable | Excluded from the general update DTOs; only `PaymentService.allocate()`/`CreditNoteService` recompute them, in the same transaction as the allocation write |
| `fin_customer_payment_method.gateway_token`/`details_json` never contain a raw card/bank number, CVV, or full account number | DTO validation rejects PAN-shaped input server-side; the endpoint that creates a payment method is a gateway-tokenization callback, never a raw-card-entry form submit |
| A write-off requires an `approved_by_user_id` distinct from the invoice's own creator | Service-layer check |
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
- **New platform capability:** `OPZFINANCE` as a provisioned logical
  database (connection pool, migration target, backup schedule) —
  [doc 28 §2](../architecture/28-per-application-database-design.md#2-rule-one-logical-database-per-sold-application).
- **Payment gateway integration:** not designed in this drop. Recording a
  `fin_payment_transaction`/tokenizing a `fin_customer_payment_method`
  assumes a payment-gateway client exists (Stripe-class or a local
  processor) — same class of not-yet-built external-integration
  dependency as `modules/mail` for SMTP/IMAP.
- `modules/apps` — this app already has a catalog entry
  (`app_key: manage-my-finance`, `opz-005`, category `finance`) in
  `modules/apps/db/seed/application_catalog.yaml`; no seed change is
  needed. `ApplicationFolderPresence` still requires
  `apps/manage-my-finance/{frontend,backend,mobile}` to exist before the
  app can be licensed — not performed in this drop (implementation only).
- **Not this app, noted as a future consideration:** `fin_customer`
  overlaps conceptually with `apps/manage-my-data`'s `dat_business` and
  `apps/manage-my-market`'s `mkt_lead` (all three model "an external
  party the company deals with"). No cross-reference is added between
  them in this drop — see
  [doc 31 §3.6](../architecture/31-finance-domain-design.md#36-no-local-identity--and-a-known-future-overlap-left-explicit-rather-than-solved)
  for why that is a deliberate, not accidental, omission.

---

## GUI Metadata Design

Every screen renders from `id_field_definition` via `GET
/api/v1/opzhub/forms/{form_id}` ([doc 22](../architecture/22-common-fields-forms-fk.md) §3).

### Screen: Customer (`manage-my-finance.customer.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `display_name` | Customer Name | `text` | Yes | Max 200 chars | `cus` `c`/`u` | — |
| `email` | Email | `text` | No | `fmt: email` | `cus` `c`/`u` | — |
| `preferred_currency` | Currency | `select` | No | ISO 4217 list (static, common `common/frontend` constant) | `cus` `c`/`u` | — |
| `tax_registration_number` | Tax ID | `text` | No | Company-defined format per `billing_country` | `cus` `c`/`u` | — |
| `status` | Status | `radio` | Yes | `ACTIVE`, `ARCHIVED` | `cus` `u` only | Archiving blocked with an inline error while open invoices exist |

### Screen: Invoice (`manage-my-finance.invoice.create` / `.edit`)

| Field Key | Heading | Type | Mandatory | Allowed Values / Format | Role Access | Subactions |
|-----------|---------|------|-----------|------------------------|-------------|-----------|
| `customer_id` | Customer | `lookup` | Yes | `fk: { res: "manage-my-finance.customer" }` | `inv` `c` | Locked after issue |
| `status` | Status | `select` | Yes | `DRAFT`,`OPEN`,`PAID`,`PARTIALLY_PAID`,`OVERDUE`,`VOID`,`UNCOLLECTIBLE` | `inv` `u` only, `mode: view` on this form | Driven by dedicated "Issue"/"Void" actions, never a free-editable dropdown here |
| `due_date` | Due Date | `datetime` | No | Date only, ≥ `invoice_date` | `inv` `c`/`u` while `DRAFT` | — |
| `amount_paid` | Amount Paid | `display` | — | Read-only, computed | `inv` `v` | — |

### Screen: Invoice Directory (list)

| Column | Heading | Type | Sortable | Role Access |
|--------|---------|------|---------|-------------|
| `invoice_number` | Invoice # | `text` | Yes | `inv` `v` |
| `customer_id` (joined) | Customer | `text` | Yes | `inv` `v` |
| `status` | Status | `status-badge` | Yes | `inv` `v` |
| `total` | Total | `money` | Yes | `inv` `v` |
| `amount_due` | Due | `money` | Yes | `inv` `v` |
| `due_date` | Due Date | `date` | Yes | `inv` `v` |
| — | Actions | `actions` | No | `inv` `v` | View, Edit (`u`, `DRAFT` only), Void (`u`) |

List screen `io`/`bulk` (doc 22 §8): `export: [csv, xlsx]`, `import: []`
(invoices are never bulk-imported — each goes through issue-time number
reservation and line-item validation), `bulk: { u: false, d: false }`
(money documents — catalog forces bulk off entirely per
[doc 22 §8](../architecture/22-common-fields-forms-fk.md#8-import-export-bulk-create--update--delete)'s
`io.money: export_only` guidance).

### Metadata-Driven Rules

- Invoice `status` is never a free-editable field on the edit form — the
  detail page always uses dedicated "Issue"/"Void" actions, same pattern
  as `manage-my-desk`'s ticket `status` and `manage-my-market`'s lead
  `owner_user_id` reassignment.
- `fin_customer_payment_method` fields never render their raw
  `gateway_token` — only `display_label` (e.g. "Visa ****4242"), with a
  "Remove" subaction, never an "Edit" subaction (payment methods are
  replaced, not edited, once tokenized).
- Money fields (`total`, `amount_due`, line-item `amount`) render via the
  common `money` field type formatted per the invoice's `currency` — no
  ad hoc `$`-prefixed string formatting in page components.

---

## Directory Placement

```
apps/manage-my-finance/         ← Application-specific (sold, licensed app)
│                                   Own logical database: OPZFINANCE (doc 28 §2)
├── backend/
├── frontend/
│   └── pages/                  ← Uses field primitives from common/frontend, not its own copies
├── mobile/
├── forms/                      ← Catalog defaults; runtime = id_field_definition (doc 26) + FormEnvelope (doc 22)
└── db/
    ├── schema/                 ← EXISTS (this drop) — 17 files, database: OPZFINANCE
    └── commands/

common/frontend/src/
├── fields/controls/
│   ├── LookupField/             ← Reused for customer_id, tax_code_id
│   └── ... (no new control types needed for the core fields — this app introduces no field kind not already in doc 22 §2)
├── theme/tokens.ts               ← Reused; no new colors
└── icons/                        ← "finance" icon_key already exists in the catalog; no new icon needed
```

No changes to `modules/identity/` or `modules/apps/` are required — see
Dependencies above.

**Rules:**
- No module-local reimplementation of `LookupField`, `DataTable`, or any
  field control ([doc 22](../architecture/22-common-fields-forms-fk.md) §10).
- `fin_tax_code` lookups are reused by every screen that needs a tax
  classification via the kernel `LookupField` + `/lookup/{res}` API.

---

## Constants

### Backend (`apps/manage-my-finance/backend/.../ManageMyFinanceConstants.java`)

```java
public static final String INVOICE_STATUS_DRAFT             = "DRAFT";
public static final String INVOICE_STATUS_OPEN               = "OPEN";
public static final String INVOICE_STATUS_PAID               = "PAID";
public static final String INVOICE_STATUS_PARTIALLY_PAID     = "PARTIALLY_PAID";
public static final String INVOICE_STATUS_OVERDUE            = "OVERDUE";
public static final String INVOICE_STATUS_VOID               = "VOID";
public static final String INVOICE_STATUS_UNCOLLECTIBLE      = "UNCOLLECTIBLE";

public static final String PAYMENT_STATUS_PENDING            = "PENDING";
public static final String PAYMENT_STATUS_SUCCEEDED          = "SUCCEEDED";
public static final String PAYMENT_STATUS_FAILED             = "FAILED";
public static final String PAYMENT_STATUS_REFUNDED           = "REFUNDED";

public static final int    INVOICE_NUMBER_MAX_LEN            = 32;
public static final int    CURRENCY_CODE_LEN                 = 3;
```

### Frontend (`apps/manage-my-finance/frontend/manageMyFinanceConstants.ts`)

```typescript
export const FINANCE_API_BASE          = "/api/v1/opzhub/manage-my-finance";
export const INVOICE_LIST_HEADING      = "Invoices";
export const INVOICE_STATUS_OPTIONS = [
  { value: "DRAFT",            label: "Draft"            },
  { value: "OPEN",              label: "Open"              },
  { value: "PAID",              label: "Paid"              },
  { value: "PARTIALLY_PAID",   label: "Partially Paid"    },
  { value: "OVERDUE",           label: "Overdue"           },
  { value: "VOID",              label: "Void"              },
  { value: "UNCOLLECTIBLE",    label: "Uncollectible"     },
];
```

Colors, spacing, and icon keys stay in `common/frontend/src/theme/tokens.ts`
and `common/frontend/src/icons/` — not duplicated here
([Rule 6](IMPLEMENTATION_RULES.md#rule-6--hardcoded-values-colors-icons-css)).

---

## Optimization, Performance & Memory

See [doc 31 §7](../architecture/31-finance-domain-design.md#7-performance-optimization-memory-domain-specific-notes)
for the domain-specific reasoning. Summary of concrete rules:

### Performance
- `fin_invoice.amount_paid`/`amount_due` and `fin_credit_note`'s
  allocation columns are maintained — no live `SUM` per row on list
  screens.
- `code_sequence.reserve_next.sql` is one atomic `UPDATE ... RETURNING`
  — no read-then-write race.
- Invoice/customer/payment list screens are always server-paginated with
  filters pushed into their `*.list_paged.sql`.

### Memory
- **React:** `InvoiceDetailPage`/`CustomerDetailPage` tabs fetch their
  own data lazily on tab-select — same pattern as every other
  `apps/manage-my-*` detail page this session.
- **Java:** collections-queue and payment-history lists stream paginated
  — never load a customer's full invoice/payment history into one list.

### Optimization
- Money is `numeric` throughout, never floating point — exactness, not
  convenience.
- `fin_customer_payment_method`/`fin_customer_custom_field` are excluded
  from the generic CRUD API and default list projections respectively —
  sensitive/high-cardinality data never rides along with a directory
  query it isn't needed for.

---

## Standard Implementation Rules

> Full rules: [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md) |
> RBAC DB design: [doc 26](../architecture/26-rbac-db-design.md) |
> Finance domain design: [doc 31](../architecture/31-finance-domain-design.md)

### Unit Tests

Tests in `managemyopz-testing/01-unit/apps/manage-my-finance/` (mirroring
the `01-unit/modules/<name>/` convention from
[Rule 1](IMPLEMENTATION_RULES.md#rule-1--unit-test-cases-separate-repo),
extended for the `apps/` vs `modules/` split, same as every other
`apps/manage-my-*` requirement doc). No test files under
`apps/manage-my-finance/backend/src/test/`.

| Class | What it tests |
|-------|--------------|
| `InvoiceServiceTest` | Line items locked after issue; `invoice_number` reservation atomicity; void transition rules |
| `PaymentServiceTest` | Allocation recomputes `amount_paid`/`amount_due` correctly; over-allocation rejected |
| `CustomerServiceTest` | Archive blocked while open invoices exist |
| `CustomerPaymentMethodServiceTest` | Raw card/bank-shaped input rejected; only gateway token/display-safe fields persisted |
| `InvoiceControllerTest` | RBAC annotation enforcement per feature id |

### RBAC in DB

Feature ids: `cus`, `inv`, `pay`, `wof`, `dsp`, `col`, `tax` (full table
and rationale: [doc 31 §6](../architecture/31-finance-domain-design.md#6-rbac--abac)).

Starter roles seeded by this app's migration:

```sql
INSERT INTO id_role (role_code, role_title, is_system) VALUES
  ('finance_admin',       'Finance Administrator', true),
  ('finance_ar_clerk',    'AR Clerk',               true),
  ('finance_collections', 'Collections Agent',      true);

INSERT INTO id_role_permission (role_code, module_id, feature_id, permissions) VALUES
  ('finance_admin',       'manage-my-finance', 'cus', 'vcud'),
  ('finance_admin',       'manage-my-finance', 'inv', 'vcud'),
  ('finance_admin',       'manage-my-finance', 'pay', 'vcud'),
  ('finance_admin',       'manage-my-finance', 'wof', 'vcu'),
  ('finance_admin',       'manage-my-finance', 'dsp', 'vcu'),
  ('finance_admin',       'manage-my-finance', 'col', 'vcu'),
  ('finance_admin',       'manage-my-finance', 'tax', 'vcud'),
  ('finance_ar_clerk',    'manage-my-finance', 'cus', 'vcu'),
  ('finance_ar_clerk',    'manage-my-finance', 'inv', 'vcu'),
  ('finance_ar_clerk',    'manage-my-finance', 'pay', 'vc'),
  ('finance_ar_clerk',    'manage-my-finance', 'dsp', 'v'),
  ('finance_collections', 'manage-my-finance', 'inv', 'v'),
  ('finance_collections', 'manage-my-finance', 'col', 'vcu');
```

`wof` (write-offs) is deliberately **not** granted to `finance_ar_clerk`
— write-offs require `finance_admin`, reflecting the Business Rule that a
write-off needs an approver distinct from routine AR handling.
`finance_collections`'s `col.u` is further narrowed by ABAC to
`resource.assigned_agent_user_id == user.id`. Company admins may layer
`id_user_permission` GRANT/REVOKE rows on top of these three starter
roles without creating new roles, per
[Rule 2](IMPLEMENTATION_RULES.md#rule-2--rbac-in-db-with-optimized-tables).

### Form Metadata in DB

Form IDs for this module:

| Form ID | Screen |
|---------|--------|
| `manage-my-finance.customer.create` | Create Customer |
| `manage-my-finance.customer.edit` | Edit Customer |
| `manage-my-finance.customer.custom` | Custom Fields (per-company defined) |
| `manage-my-finance.invoice.create` | Create Invoice |
| `manage-my-finance.invoice.edit` | Edit Invoice |
| `manage-my-finance.invoice.list` | Invoice Directory |
| `manage-my-finance.credit-note.edit` | Credit Note Editor |
| `manage-my-finance.payment-transaction.edit` | Record Payment |
| `manage-my-finance.collections-queue-item.edit` | Collections Working View |
| `manage-my-finance.tax-code.edit` | Tax Code Admin |

Example migration row (`status` field on the invoice edit form,
view-only, driven by dedicated actions):

```sql
INSERT INTO id_field_definition (
  form_id, field_key, field_heading, field_type,
  is_mandatory, display_order, allowed_values, role_visibility
) VALUES (
  'manage-my-finance.invoice.edit', 'status', 'Status', 'select',
  true, 1,
  '[{"value":"DRAFT","label":"Draft"},{"value":"OPEN","label":"Open"},
    {"value":"PAID","label":"Paid"},{"value":"PARTIALLY_PAID","label":"Partially Paid"},
    {"value":"OVERDUE","label":"Overdue"},{"value":"VOID","label":"Void"},
    {"value":"UNCOLLECTIBLE","label":"Uncollectible"}]'::jsonb,
  '{"default":"readonly"}'::jsonb
);
```

### API-Level RBAC/ABAC

| Endpoint | Auth | RBAC | ABAC |
|----------|------|------|------|
| `POST /invoices` | `SessionAuthFilter` (401) | `inv.c` | Tenant-scoped only |
| `PUT /invoices/{id}/issue` | 401 | `inv.u` | — |
| `POST /write-offs` | 401 | `wof.c` | `finance_admin` only in practice (no AR-clerk grant) |
| `PUT /collections-queue/{id}` | 401 | `col.u` | `resource.assigned_agent_user_id == user.id` for `finance_collections` |
| `GET`/`PUT /tax-codes` | 401 | `tax.v`/`tax.u` | `finance_admin` only |

Every controller method carries `@RequiresPermission(module =
"manage-my-finance", feature = <id>, action = <letter>)`
([Rule 4](IMPLEMENTATION_RULES.md#rule-4--rbacabac-at-api--backend-level)).
ABAC row scoping is evaluated in the service layer via
`accessService.requireAbac(...)` / `accessService.rowFilter(...)`.

### Coding Standards (this feature)

**Java:** Domain entities (`Customer`, `Invoice`, `PaymentTransaction`,
...) are immutable `record`s. All SQL in
`apps/manage-my-finance/db/commands/`. No business logic in
controllers — delegate to `*Service`. Constants in
`ManageMyFinanceConstants.java` only. Monetary fields use `BigDecimal`
end to end — never `double`/`float`.

**Flutter:** `ManageMyFinanceConstants` class in
`apps/manage-my-finance/mobile/lib/constants/`. All
`TextEditingController` instances disposed in `dispose()`.

**TypeScript/React:** `INVOICE_STATUS_OPTIONS` and `FINANCE_API_BASE` in
`manageMyFinanceConstants.ts`. `LookupField` and `DataTable` imported
from `common/frontend` — not re-implemented.

### Directory Confirmation

```
apps/manage-my-finance/
    backend/          ← all controller + service + repository Java
    frontend/          ← pages using common field primitives
    mobile/            ← Flutter pages using common widgets
    forms/             ← catalog defaults compiled into FormEnvelope
    db/schema/         ← 17 schema YAML files (this drop), database: OPZFINANCE
    db/commands/       ← all SQL named commands (implementation)
common/frontend/src/
    theme/tokens.ts    ← reused, unchanged
```
