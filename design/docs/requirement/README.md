# Requirements

Individual requirement documents for each common feature. Each file is self-contained and intended to be handed directly to the implementing developer.

## Index

| File | Feature | Module | DB |
|------|---------|--------|----|
| [login-routing.md](login-routing.md) | Login auto routing — company resolve, UseCase 1 & 2 | `identity` (extend) | `opzmain` |
| [company-setup.md](company-setup.md) | Company creation, server details, backend endpoint management | `company-setup` (new) | `opzmain` |
| [change-password.md](change-password.md) | Change own password | `identity` (extend) | `opzuser` |
| [user-settings.md](user-settings.md) | User profile & preferences | `user-settings` (new) | `opzuser` |
| [backup-restore.md](backup-restore.md) | Database backup & restore | `backup-restore` (new) | `opzmain` |
| [maintenance.md](maintenance.md) | Maintenance mode | `maintenance` (new) | `opzmain` |
| [manage-my-people.md](manage-my-people.md) | Generic person master (profile, org affiliation, documents, identifiers, skills, timeline) plus a category-agnostic attendance & leave core — reusable base for future `manage-my-hra` / `manage-my-students` | `apps/manage-my-people` (new) | `opzpeople` (own DB, [doc 28](../architecture/28-per-application-database-design.md)) |
| [manage-my-data.md](manage-my-data.md) | Business/lead data directory (intake → assignment → verification → publish workflow, categories, locations, public inquiries/reviews) | `apps/manage-my-data` (new) | `opzdata` (own DB, [doc 28](../architecture/28-per-application-database-design.md)) |
| [manage-my-market.md](manage-my-market.md) | Lead generation & marketing operations (unified lead/CRM, campaigns, journeys, telemarketing, referrals) — renamed from `manage-my-marketing` | `apps/manage-my-market` (renamed) | `opzmarket` (own DB, [doc 28](../architecture/28-per-application-database-design.md)) |
| [manage-my-finance.md](manage-my-finance.md) | Accounts-receivable / billing-to-cash (customers, invoices, payments, credit notes, collections) | `apps/manage-my-finance` (new) | `opzfinance` (own DB, [doc 28](../architecture/28-per-application-database-design.md)) |
| [manage-my-desk.md](manage-my-desk.md) | Paid service-ticket desk (commercial-clearance gate, SLA clock, teams, activity monitoring/timesheets) | `apps/manage-my-desk` (new) | `opzdesk` (own DB, [doc 28](../architecture/28-per-application-database-design.md)) |
| [manage-my-hr.md](manage-my-hr.md) | Presentation-only wrapper over `apps/manage-my-people` (category-locked to `employee`) — **no schema of its own** | `apps/manage-my-hr` (new) | none — reuses `opzpeople` via `apps/manage-my-people`'s API |
| [dashboard-layout.md](dashboard-layout.md) | Configurable drag-and-drop dashboard layout with widget catalog, named templates, per-role layouts, and per-user personalisation — shared by all applications | `dashboard-layout` (new — common module) | `opzmain` (catalog, templates) + `opzhub` per-company (active layouts, user overrides) |
| [platform-admin-login.md](platform-admin-login.md) | Central admin login with no company context — bare username on the existing login field, scoped only to company-setup | `identity` (extend) | `opzmain` (new `platform_admin` table) |
| [product-catalog.md](product-catalog.md) | Product grouping above company (managemyopz, managemyid, smartaicampus); single-source product→apps catalog driving both the DB seed and product-scoped packaging | `apps` (extend) + `licensing` (schema) | `opzmain` (new `product_catalog`) + `opzhub` (product_key on application_catalog) |

## Implementation Order

1. **login-routing** — depends on `company_information` v2 schema; unblocks all other features.
2. **company-setup** — creates and manages the registry entries routing depends on.
3. **change-password** — lightweight extension to `identity`; no new tables.
4. **user-settings** — isolated; only needs `identity` for session.
5. **backup-restore** — needs `company_information` and `server_details` in `opzmain`.
6. **maintenance** — needs `identity` for RBAC; adds a kernel filter.
7. **manage-my-people** — needs `company-setup` (`company_information`), `identity` RBAC tables (doc 26), and a provisioned `OPZPEOPLE` logical database (doc 28); no schema change to `identity` itself is required — see the requirement's Dependencies section. Includes a category-agnostic attendance & leave core (no separate dependency — same `OPZPEOPLE` database, same `id_user` cross-references).
8. **manage-my-data** — needs `company-setup`, `identity` RBAC tables, and a provisioned `OPZDATA` logical database (doc 28); no dependency on `manage-my-people` or `manage-my-desk`. Public inquiry/review/directory endpoints additionally need gateway-level rate limiting and CAPTCHA before they are safe to expose.
9. **manage-my-market** — needs `company-setup`, `identity` RBAC tables, a provisioned `OPZMARKET` logical database (doc 28), and the `modules/apps` catalog rename (`manage-my-marketing` → `manage-my-market`, performed in this drop). Optionally reads `apps/manage-my-people`'s `ppl_person` for agent HR-profile enrichment, but does not require it. SEO monitoring and social media management are a future phase — see the requirement's Dependencies section.
10. **manage-my-finance** — needs `company-setup`, `identity` RBAC tables, and a provisioned `OPZFINANCE` logical database (doc 28); no dependency on any other `apps/manage-my-*` app. Actual payment-gateway capture/tokenization is a future external-integration dependency, not functional until that exists — see the requirement's Dependencies section.
11. **manage-my-desk** — needs `company-setup`, `identity` RBAC tables, and a provisioned `OPZDESK` logical database (doc 28). Optionally reads `apps/manage-my-people`'s `ppl_org_unit` for department enrichment, but does not require it. Payment-gateway capture, notification send mechanics, and the client-side activity-capture agent are future external dependencies — see the requirement's Dependencies section.
12. **manage-my-hr** — needs `apps/manage-my-people` fully deployed first (**required**, not optional — this app has no data or schema of its own). No database, no migration, no RBAC seed of its own; frontend-only, composing `apps/manage-my-people`'s existing pages with `category=employee` locked. See the requirement's Dependencies section.
13. **dashboard-layout** — can be implemented independently after `identity` RBAC tables exist (doc 26). Each application registers its widgets via YAML seed entries; no application-level code change is required. The `DashboardRenderer` common component is the only integration point for application dashboard pages.
14. **platform-admin-login** — extends `identity`'s existing `AuthService`/`IdentityController`; no new endpoint. Independent of every other feature in this list.
15. **product-catalog** — independent of `platform-admin-login`. Depends only on `apps`'s existing `ApplicationCatalogSeed` and `licensing`'s `company_information` schema (v3). Packaging (`infra/wrappers/package-product.sh`) has no runtime dependency at all — it only reads `platform/catalog/products.yaml`.

## Architecture Reference

Full design with routing architecture and directory tree:
[design/docs/architecture/25-common-features-design.md](../architecture/25-common-features-design.md)
