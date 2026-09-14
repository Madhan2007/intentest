# 35 — Platform Admin Login, Product Catalog, and Product-Scoped Packaging

## 1. Overview

Three related capabilities, all centered on a new top-level concept — the
**product** — sitting above `company`:

| Concern | Summary |
|---------|---------|
| Platform admin login | A central administrator account with no `company_id`, scoped only to managing companies and licenses. Triggered by typing a bare username on the normal login field. |
| Product catalog | A product (e.g. `managemyopz`, `managemyid`, `smartaicampus`) groups a subset of `apps/<id>/` folders under one packaged, branded offering. |
| Product-scoped packaging | `infra/wrappers/package-product.sh` builds a subset tree containing only the apps that belong to the requested product. |
| Branding layering | Product-level default branding (build time) is shown before a company is resolved; company branding (runtime) overrides it after. |

This extends — does not replace — the existing routing model confirmed in
this session: one centralized server (`opzmain` company/license registry),
per-company data reached through `DataRouter` after login
([25](25-common-features-design.md) §3).

---

## 2. Platform Admin Login

### 2.1 Login identifier grammar

The single `/login` endpoint now resolves three identifier shapes, tried in
this order (implemented in `AuthService.findUserByLoginIdentifier`):

| Typed value | Resolved as | Table | Database |
|-------------|------------|-------|----------|
| Contains `@` | Email → company by `company_references` domain match | `id_user` | `opzuser` (per company) |
| Contains `/` | `companyReference/username` | `id_user` | `opzuser` (per company) |
| Neither (bare username) | Platform admin | `platform_admin` | `opzmain` (central) |

No new endpoint, no new screen, no reserved prefix character. A user who
types just `platformadmin` (no slash, no `@`) is routed to the
company-less platform-admin lookup. This was chosen over a `#admin/` prefix
or a separate screen because the login field's existing grammar already
distinguishes tenant identifiers by the presence of `/` or `@` — a bare
string was previously simply rejected, so repurposing it introduces no
ambiguity with any valid tenant identifier.

### 2.2 Session shape

A platform admin session carries **no `company_id`** — `SessionData` and
`LoginResult` already declare `companyId` as nullable, and
`IdentityController` already omits the `c` key from the login/session
response when it is blank. No API contract change was needed for this.

```json
{
  "ok": true,
  "data": {
    "user": { "id": "...", "n": "Platform Administrator", "r": ["platform_admin"] },
    "a": { "company-setup": { "*": "vcua" } },
    "token": "..."
  }
}
```

Compare to a tenant admin session, which always carries `"c": "<company_id>"`
and a matrix keyed by the tenant's enabled modules.

### 2.3 Permission scope

`AuthService.buildMatrix` grants a `platform_admin` role session **only**:

```
company-setup.* = vcua
```

No wildcard module access, no tenant module access. This is intentionally
narrower than `ROLE_SUPERUSER` (which grants view-only wildcard access to
every module) — a platform admin manages the company/license registry and
nothing else. It is never routed to a company database because it has no
`company_id` to route with; `DataRouter` (when implemented) must treat a
null `company_id` session as "stay on OPZMAIN, never switch pools."

### 2.4 Storage: `platform_admin` (OPZMAIN)

```
platform_admin (OPZMAIN)
├── id             UUID PK
├── username       TEXT UNIQUE NOT NULL
├── display_name   TEXT NOT NULL
├── password_hash  TEXT NOT NULL       — Argon2id, same encoder as id_user
├── enabled        BOOLEAN DEFAULT true
└── created_at     TIMESTAMPTZ DEFAULT now()
```

Deliberately separate from `id_user` (which lives in per-company `opzuser`
and always requires a `company_id`). Mixing the two would force `id_user`'s
`company_id` to become nullable everywhere it's already relied upon.

### 2.5 Bootstrap

`DefaultPlatformAdminBootstrap` mirrors `DefaultAdminBootstrap`:

- Runs once at startup; creates exactly one row when `platform_admin` is
  empty.
- Username: `platformadmin` (fixed; avoids colliding with any tenant's
  `admin` username, which lives in a different table anyway).
- Password: `OPZHUB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD` env var if set,
  otherwise a generated 24-character password written to
  `/etc/opzhub/bootstrap-platform-admin-password` (matches the existing
  tenant-admin credential file convention).
- Never overwrites an existing platform admin or its password.

Retrieve it exactly like the tenant bootstrap password:

```bash
MSYS_NO_PATHCONV=1 docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml \
    exec opzhub-be-app cat /etc/opzhub/bootstrap-platform-admin-password
```

### 2.6 Code map

| Class | Package | Role |
|-------|---------|------|
| `PlatformAdminRepository` | `modules/identity/.../data` | Port bound to OPZMAIN |
| `DataClientPlatformAdminRepository` | `modules/identity/.../data` | Postgres implementation |
| `DefaultPlatformAdminBootstrap` | `modules/identity/.../application` | One-time bootstrap |
| `AuthService` (extended) | `modules/identity/.../application` | Bare-username branch + matrix |

SQL commands: `modules/identity/db/commands/identity.platform_admin.{find_by_username,count,create}.sql`.

---

## 3. Product Catalog

### 3.1 Why a product sits above company

A **product** is a packaged, branded offering — e.g. `managemyopz` today,
with `managemyid` and `smartaicampus` planned. Every `apps/<id>/` folder
belongs to exactly one product. A company is onboarded under one product
(the one its deployment was built for); a single physical server build is
single-product because packaging strips every other product's app folders
before the image is even built.

```
product (managemyopz)
   └── apps/manage-my-data, apps/manage-my-desk, ... (many)
          └── company (Technosprint, onboarded under managemyopz)
                 └── users, data (per-company opzuser/opzhub)
```

### 3.2 Single source of truth: `platform/catalog/products.yaml`

```yaml
products:
  - product_key: managemyopz
    product_name: ManageMyOpz
    logo_uri: /branding/products/managemyopz/logo.svg
    icon_key: managemyopz
    status: active
    apps: [manage-my-data, manage-my-desk, manage-my-hr, ...]
  - product_key: managemyid          # placeholder — no apps yet
    apps: []
  - product_key: smartaicampus       # placeholder — no apps yet
    apps: []
```

This one file feeds three consumers — no other file needs to be kept in
sync by hand:

1. **`ProductCatalogSeed`** (folded into `ApplicationCatalogSeed.seed()`,
   `modules/apps`) — upserts OPZMAIN's `product_catalog` rows.
2. **`ApplicationCatalogSeed`**'s product_key backfill — for every
   `app_key` listed under a product, patches
   `application_catalog.product_key` in OPZHUB.
3. **`infra/wrappers/package-product.sh`** — reads the same file directly
   (no DB dependency) to decide which `apps/<id>/` folders to copy into a
   product-scoped build.

### 3.3 Schema

```
product_catalog (OPZMAIN)
├── product_key   TEXT PK
├── product_name  TEXT NOT NULL
├── description   TEXT
├── logo_uri      TEXT           — default branding, doc 35 §4
├── icon_key      TEXT
├── status        TEXT DEFAULT 'active'
└── created_at    TIMESTAMPTZ DEFAULT now()

company_information (OPZMAIN) — v3 addition
└── product_key   TEXT NULLABLE DEFAULT 'managemyopz'
    FK -> product_catalog.product_key (real Postgres FK; same database)

application_catalog (OPZHUB) — addition
└── product_key   TEXT NULLABLE DEFAULT 'managemyopz'
    (cross-database reference to OPZMAIN.product_catalog — app-enforced
     only, same caveat as company_information.server_name)

platform_product (OPZMAIN) — singleton, doc 35 §4.5
├── singleton_key  TEXT PK, always 'default'
├── product_key    TEXT NOT NULL, FK -> product_catalog (real FK, same DB)
└── activated_at   TIMESTAMPTZ DEFAULT now()

company_active_product (OPZHUB) — singleton mirror, doc 35 §4.5
├── singleton_key  TEXT PK, always 'default'
├── product_key    TEXT NOT NULL (cross-database ref, app-enforced only)
└── activated_at   TIMESTAMPTZ DEFAULT now()
```

`product_key` on `company_information` is nullable for the same
reconciliation reason `company_slug` is nullable (doc 25/26 migration
note): existing rows must not block the `ALTER TABLE`. The FK is added by
the schema reconciler on a best-effort basis (same non-fatal `WARN`-and-
continue behavior already established for `fk_company_information_endpoint_name`).
On a fresh database it succeeds once `product_catalog` has been seeded —
`ApplicationCatalogSeed` seeds `product_catalog` before backfilling any
`product_key` value, so a full container restart after first boot is
sufficient for the reconciler to add the FK cleanly.

### 3.4 Startup ordering

```
1. Schema reconciliation (adds columns; FK best-effort, see above)
2. ApplicationCatalogSeed.seed()
   a. Upsert application_catalog rows from modules/apps/db/seed/application_catalog.yaml
   b. seedProductCatalog()      — upsert product_catalog from products.yaml
   c. backfillProductKeys()     — patch application_catalog.product_key
3. DefaultPlatformAdminBootstrap.bootstrap()
4. DefaultAdminBootstrap.bootstrap()
```

---

## 4. Product-Scoped Packaging

### 4.1 Command

```bash
# From repo root
bash infra/wrappers/package-product.sh --product managemyopz --out dist/managemyopz

# Default product is managemyopz when --product is omitted
bash infra/wrappers/package-product.sh
```

`--product` is named on a command line in **exactly this one place** —
nowhere else in the system. This is deliberate: a human packaging a build
decides the product once; every component downstream of that decision
(the seed at boot, the running application, any future operator tooling)
must find out which product it is by **reading a value that was written
down**, not by being told again. §4.5 below covers how that value reaches
the running system without a repeated argument.

### 4.2 Algorithm

```
inputs:  repo root, --product KEY (default managemyopz), platform/catalog/products.yaml
output:  <out>/ containing common/, modules/, platform/, infra/, tools/,
         gateway/ (whole) + apps/<id>/ (only apps listed for KEY)

apps_for_product = products.yaml[KEY].apps
included = apps_for_product ∩ {folders that exist under apps/}
missing  = apps_for_product − included      (listed but no folder yet)
excluded = {folders under apps/} − included  (belong to another product)

refuse if included is empty (placeholder products with apps: [] refuse to build)
copy shared trees wholesale
copy only apps/<id>/ for id in included
write <out>/platform/config/product.yaml   { product_key: KEY }   (§4.5)
write <out>/PRODUCT_BUILD.md documenting included/missing/excluded
```

This composes with, but is distinct from, the customer `solution.manifest.yaml`
mechanism already documented in [10](10-solution-composition.md) §2 — product
packaging is the **first** filter (which apps exist in this product build
at all); a customer solution pack built from a product's output can still
apply a second, narrower filter (which of that product's apps one specific
customer buys). Product packaging does not touch `modules/*` — those are
cross-product shared infrastructure (identity, admin, company-setup,
dashboard-layout, ...), not product-specific.

### 4.3 Why this is a standalone script, not part of `opzhubctl`

Two independent reasons, both hard requirements:

1. **Timing.** `infra/wrappers/run-opzhubctl.sh` enforces `uid 2100`
   (`tsuser`) via `opzhub_assert_tsuser` — correct for an *operator*
   command running inside an already-deployed container. Packaging is a
   **build-time host step** that produces the input tree for the *next*
   `docker build`, before any container exists. There is no running
   container, and no `tsuser`, at the point packaging happens.
2. **Naming collision avoided.** `opzhubctl package --solution <path>`
   is a pre-existing, separate design
   ([doc 01](01-plugin-play-model.md) §6,
   [doc 06](06-scripts-design.md), [doc 10](10-solution-composition.md) §2,
   [doc 17](17-dev-prod-implementation.md) §4) — it filters `modules/<id>/`
   folders for one customer's `solution.manifest.yaml`. This document's
   packaging concern is a **different axis**: filtering `apps/<id>/`
   folders for one product. Reusing the `package` verb for both would
   silently overload one command with two unrelated filters. Do not wire
   `--product` into `opzhubctl package` when that command is eventually
   implemented — it takes `--solution`, never `--product`.

The two compose in one direction only: run `package-product.sh` first to
get a product-scoped tree; a customer solution pack can then be built
*from that tree's output* with the (still unimplemented) `opzhubctl
package --solution`. Never the other way around.

### 4.4 Adding a new product

1. Add a block to `platform/catalog/products.yaml` with `product_key`,
   `product_name`, branding fields, and an `apps:` list.
2. Build the listed `apps/<id>/` folders (ordinary app development —
   nothing product-specific required in the app code itself).
3. Restart the backend once so `ProductCatalogSeed` and the product_key
   backfill run against the new entry.
4. `bash infra/wrappers/package-product.sh --product <key>` now succeeds.

No Java code change is required to add a product — the seed and the
packaging script both read `products.yaml` only.

### 4.5 How the running system learns its product without `--product`

The chain from "a human typed `--product managemyid`" to "the running
application, and anything querying its database, knows this deployment is
`managemyid`" has exactly one command-line argument in it — the rest is
a file write followed by a database write, both automatic:

```
package-product.sh --product managemyid
        │  writes
        ▼
<out>/platform/config/product.yaml     { product_key: managemyid }
        │  (this file ships inside the built container image)
        ▼
Container starts. PlatformProductSeed.seed() runs at boot:
        │  reads platform/config/product.yaml (defaults to "managemyopz"
        │  if absent — i.e. running straight from the vendor monorepo)
        ▼
Upserts ONE row into BOTH:
  ├── platform_product          (OPZMAIN)  — singleton_key = 'default'
  └── company_active_product    (OPZHUB)   — singleton_key = 'default'
        │
        ▼
Any later code that needs "which product is this deployment" —
branding renderer, catalog filtering, a future admin screen — queries
platform_product (or company_active_product for a COMPANY_BE deployment
that may never reach OPZMAIN) instead of accepting a runtime argument.
```

Both tables are schema-driven singletons: a fixed literal
`singleton_key = 'default'` primary key guarantees exactly one row ever
exists per database. `platform_product.product_key` carries a real
Postgres FK to `product_catalog` (same database); `company_active_product`
is a cross-database reference to the same catalog, app-enforced only —
same caveat as every other OPZHUB→OPZMAIN reference in this codebase.

**Why duplicate the marker into OPZHUB too:** a `COMPANY_BE` deployment
(doc 25 §3.3) runs as its own full ManageMyOpz backend, managing its own
database, and does not necessarily have OPZMAIN connectivity at all. It
still needs to answer "which product am I" — for branding, for filtering
its own `application_catalog` display — so the marker is written locally,
into the same database it already owns.

---

## 5. Branding Layering (placeholder)

Two layers, resolved at different times:

| Layer | When | Source | Scope |
|-------|------|--------|-------|
| Product default | Page load, before any company is typed | `product_catalog.logo_uri` (OPZMAIN) / `common/frontend/src/theme/products/<key>/product.theme.json` (build-time bundled) | Whole product build |
| Company override | After `/resolve` returns (doc 25 §3.4) | `company_information.logo_uri`, `company_name` | One company, for the rest of the session |

```
Login screen mounts
        │
        ▼
Show product default (logo, name, primary_color)
   from bundled product.theme.json — no network call needed
        │
        │  user types "acme/ada"
        ▼
POST /resolve  →  { logo: "/brand/acme.svg", n: "Acme Ltd", ... }
        │
        ▼
Swap to company branding for password entry onward
```

### 5.1 Placeholder locations (this drop)

```
common/frontend/src/theme/products/
├── PLACEHOLDER.md                — resolution order, schema notes
├── managemyopz/product.theme.json
├── managemyid/product.theme.json       (placeholder values; no apps yet)
└── smartaicampus/product.theme.json    (placeholder values; no apps yet)

common/mobile/lib/theme/products/       — same structure, Flutter mirror
```

`product.theme.json` shape:

```json
{
  "product_key": "managemyopz",
  "product_name": "ManageMyOpz",
  "logo_uri": "/branding/products/managemyopz/logo.svg",
  "icon_key": "managemyopz",
  "primary_color": "#2563eb"
}
```

No frontend code reads these files yet — wiring `ThemeProvider.tsx` /
`tokens.dart` to load the bundled `product.theme.json` for the packaged
product, and to swap in company branding on `/resolve`, is a placeholder
for a future drop. The DB-side default (`product_catalog.logo_uri`) already
exists and can be served by a future `GET /platform/product` endpoint if a
runtime (non-bundled) lookup is preferred over a build-time bundled file.

---

## 6. Cross-References

- Company/login routing baseline: [25 — Common Features Design](25-common-features-design.md) §3
- RBAC/permission matrix mechanics: [26 — RBAC DB Design](26-rbac-db-design.md)
- Packaging algorithm this extends: [10 — Solution Composition](10-solution-composition.md) §2
- Standalone dev seed script: `infra/wrappers/dev-seed.sh`
