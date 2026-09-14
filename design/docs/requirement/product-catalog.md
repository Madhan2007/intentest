# Requirement: Product Catalog and Product-Scoped Packaging

**Module:** `apps` (extension) + `licensing` (schema only)
**DB target:** `opzmain` (new table `product_catalog`, new column on `company_information`) + `opzhub` (new column on `application_catalog`)
**Access level:** Seeded at startup; no end-user API in this drop
**Architecture ref:** [doc 35 §3–4](../architecture/35-platform-admin-and-product-catalog.md)
**Status:** Implemented (schema + seed + packaging script). GUI/API for
managing products is out of scope for this drop — see Dependencies.

---

## What It Does

Introduces a **product** as the grouping above `company`: a product (e.g.
`managemyopz`, `managemyid`, `smartaicampus`) owns a fixed subset of
`apps/<id>/` folders. All app-name handling now aligns under a product
instead of being flat. This enables:

1. A single authoritative catalog file (`platform/catalog/products.yaml`)
   that drives both the runtime DB seed and the build-time packaging
   filter — no double bookkeeping.
2. `infra/wrappers/package-product.sh --product <key>` to build a subset
   tree containing only that product's apps (plus all shared `common/`,
   `modules/`, `platform/`, `infra/`, `tools/`, `gateway/`).
3. Every company (`company_information`) records which product it was
   onboarded under.

---

## DB Schema

### `product_catalog` — in `opzmain`

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `product_key` | text | No | — | PK, e.g. `managemyopz` |
| `product_name` | text | No | — | |
| `description` | text | Yes | null | |
| `logo_uri` | text | Yes | null | Default branding before a company is resolved (doc 35 §5) |
| `icon_key` | text | Yes | null | |
| `status` | text | No | `'active'` | `active` \| `deprecated` |
| `created_at` | timestamptz | No | `now()` | |

### `company_information` — v3 addition (in `opzmain`)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `product_key` | text | Yes | `'managemyopz'` | FK → `product_catalog.product_key` (real Postgres FK, same database). Nullable during reconciliation for the same reason `company_slug` is — existing rows must not block the `ALTER TABLE`. |

### `application_catalog` — addition (in `opzhub`)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `product_key` | text | Yes | `'managemyopz'` | Cross-database reference to `opzmain.product_catalog` — app-enforced only, same caveat as `company_information.server_name` |

### `platform_product` — singleton, in `opzmain` (doc 35 §4.5)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `singleton_key` | text | No | — | PK, always the literal `'default'` — guarantees exactly one row |
| `product_key` | text | No | — | FK → `product_catalog.product_key` (real Postgres FK, same database) |
| `activated_at` | timestamptz | No | `now()` | |

### `company_active_product` — singleton mirror, in `opzhub` (doc 35 §4.5)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `singleton_key` | text | No | — | PK, always the literal `'default'` |
| `product_key` | text | No | — | Cross-database reference to `opzmain.product_catalog` — app-enforced only |
| `activated_at` | timestamptz | No | `now()` | |

These two singleton tables are how the running system knows which product
it is **without ever taking `--product` as a runtime argument** — see
"Runtime Auto-Detection" below.

---

## Single Source of Truth: `platform/catalog/products.yaml`

```yaml
products:
  - product_key: managemyopz
    product_name: ManageMyOpz
    description: Multi-company operations suite ...
    logo_uri: /branding/products/managemyopz/logo.svg
    icon_key: managemyopz
    status: active
    apps:
      - manage-my-data
      - manage-my-desk
      - manage-my-hr
      - manage-my-people
      - manage-my-market
      - manage-my-finance
      - manage-my-sales
      - manage-my-inventory
      - manage-my-shop
      - manage-my-project
      - manage-my-vault

  - product_key: managemyid       # planned product; placeholder
    apps: []

  - product_key: smartaicampus    # planned product; placeholder
    apps: []
```

An app not listed under any product's `apps:` keeps `product_key = NULL`
in `application_catalog` and is excluded from every product-scoped
package build.

---

## Seed Behavior

Folded into the existing `ApplicationCatalogSeed` (`modules/apps`), run at
every startup via its existing `ApplicationRunner`:

```
1. Existing application_catalog seed (unchanged)
2. seedProductCatalog(products)     — upsert product_catalog rows (OPZMAIN)
3. backfillProductKeys()            — patch application_catalog.product_key
                                       for every app_key listed under a product
```

Log lines to expect on a clean startup:

```
Application catalog seed complete (inserted=0, updated=0)
Product catalog seed complete (inserted=3)
Application catalog product_key backfill complete (updated=0)
```

The `company_information.product_key` → `product_catalog` foreign key is
added by the schema reconciler on a best-effort basis. On the very first
boot it may log a non-fatal `WARN` if `product_catalog` did not yet have
the `managemyopz` row when reconciliation ran; a subsequent restart adds
the FK cleanly once the row exists. This matches the existing behavior
already documented for `fk_company_information_endpoint_name`.

---

## Runtime Auto-Detection (no `--product` outside packaging)

`--product` is named on a command line in **exactly one place**:
`infra/wrappers/package-product.sh`. Nothing else in the system — not
`run-opzhubctl.sh`, not the seed at boot, not the Java application — ever
takes a `--product` argument. This is enforced by design, not convention:

1. `package-product.sh --product <key>` writes
   `<out>/platform/config/product.yaml` (`product_key: <key>`) into the
   built package. This file ships inside the container image.
2. At every startup, `PlatformProductSeed` (new, in `modules/apps`) reads
   that file — defaulting to `managemyopz` when it does not exist, i.e.
   when running unpackaged straight from the vendor monorepo.
3. It upserts a single row into **both** `platform_product` (OPZMAIN) and
   `company_active_product` (OPZHUB), keyed by the fixed literal
   `singleton_key = 'default'` so exactly one row ever exists per database.
4. Any later code needing "which product is this deployment" — a
   branding renderer, catalog filtering, a future admin screen — queries
   one of these two tables. A `COMPANY_BE` deployment (doc 25 §3.3), which
   manages its own database and may never reach OPZMAIN, reads
   `company_active_product` locally instead.

`run-opzhubctl.sh` was **not** given a `package` verb — see
[doc 35 §4.3](../architecture/35-platform-admin-and-product-catalog.md)
for why conflating this with the pre-existing, separate
`opzhubctl package --solution` design (doc 01 §6) would have been wrong.

---

## Packaging Command

```bash
# From repo root — default product is managemyopz
bash infra/wrappers/package-product.sh

# Explicit product and output directory
bash infra/wrappers/package-product.sh --product managemyopz --out dist/managemyopz
```

**Algorithm:**

1. Read the requested product's `apps:` list from `products.yaml`.
2. Refuse (`exit 1`) if the list is empty or the product key is unknown —
   this is why `managemyid` and `smartaicampus` cannot be packaged yet.
3. Split listed apps into `included` (folder exists) vs `missing` (listed
   but no folder yet).
4. Compute `excluded` — folders under `apps/` that belong to a different
   product or no product at all.
5. Copy `common/`, `modules/`, `platform/`, `infra/`, `tools/`, `gateway/`
   wholesale; copy only `included` apps into the output's `apps/`.
6. Write `<out>/PRODUCT_BUILD.md` documenting included/missing/excluded.

**Sample output:**

```
==> Packaging product 'managemyopz'
    Apps requested: manage-my-data manage-my-desk manage-my-hr ...
==> Copying shared trees (common, modules, platform, infra, tools, gateway)
==> Copying included apps/ folders

==> Product build complete: dist/managemyopz
    Included apps  : manage-my-data manage-my-desk manage-my-hr ...

Next: docker compose -f dist/managemyopz/infra/docker-compose.yml build
```

**Placeholder product refusal:**

```
$ bash infra/wrappers/package-product.sh --product managemyid
ERROR: product 'managemyid' not found in products.yaml, or its apps: list is empty.
       Placeholder products (managemyid, smartaicampus) cannot be packaged
       until at least one apps/<id>/ folder is listed for them.
```

This composes with, not replaces, the existing customer
`solution.manifest.yaml` packaging mechanism ([doc 10](../architecture/10-solution-composition.md)
§2) — product packaging is the first, coarser filter (which apps exist in
this product's build at all); a customer solution pack narrows further.

---

## Business Rules

- Every `apps/<id>/` folder must be listed under exactly one product's
  `apps:` list to be included in any package build.
- Adding a new product requires **no Java code change** — add a block to
  `products.yaml`, build its listed app folders, restart once so the seed
  picks it up, then package.
- `company_information.product_key` defaults to `managemyopz` for
  backward compatibility with the single-product deployments that predate
  this feature.
- The packaging script never touches `modules/*` — those are cross-product
  shared infrastructure (identity, admin, company-setup, dashboard-layout,
  ...), always included regardless of product.

---

## Directory Placement

```
platform/catalog/
├── applications.yaml       — existing vendor-wide app index (unchanged)
└── products.yaml            — NEW: product → app-list + branding, single source of truth

modules/licensing/db/schema/
├── product_catalog.yaml           — NEW (OPZMAIN)
├── platform_product.yaml          — NEW (OPZMAIN, singleton — doc 35 §4.5)
└── company_information.yaml       — v3: + product_key column/FK

modules/apps/backend/src/main/java/com/managemyopz/modules/apps/
├── application/ApplicationCatalogSeed.java   — extended: seedProductCatalog, backfillProductKeys
├── application/PlatformProductSeed.java      — NEW: reads product.yaml stamp, seeds both singletons
└── data/AppsDataConstants.java               — + PRODUCTS_CATALOG_RESOURCE, PRODUCT_KEY, ...

modules/apps/db/schema/
├── application_catalog.yaml         — + product_key column
└── company_active_product.yaml      — NEW (OPZHUB, singleton mirror — doc 35 §4.5)

infra/wrappers/
├── package-product.sh       — the packaging command; --product lives ONLY here;
│                               also stamps platform/config/product.yaml on output
└── run-opzhubctl.sh          — unchanged; no `package` verb added (see Runtime
                                 Auto-Detection above for why)

common/backend/
├── pom.xml                   — + platform/catalog resource on the build classpath
└── Dockerfile                — build stage now COPYs platform/ (needed for the classpath resource)
```

---

## Constants

All product-related literals live in `AppsDataConstants` — no product key,
resource path, or column name is inlined at any call site.

```java
// modules/apps/backend/src/main/java/com/managemyopz/modules/apps/data/AppsDataConstants.java
public static final String PRODUCTS_CATALOG_RESOURCE = "classpath:platform/catalog/products.yaml";
public static final String PRODUCTS_KEY   = "products";
public static final String PRODUCT_KEY    = "product_key";
public static final String PRODUCT_APPS_KEY = "apps";
```

`PlatformProductSeed` (the runtime auto-detection seed) keeps its own
small, self-contained constant set rather than reaching into
`AppsDataConstants` for unrelated singleton-table column names — each
class owns the literals for the table it directly manages:

```java
// modules/apps/backend/src/main/java/com/managemyopz/modules/apps/application/PlatformProductSeed.java
private static final String PRODUCT_STAMP_FILE = "platform/config/product.yaml";
private static final String DEFAULT_PRODUCT_KEY = "managemyopz";
private static final String SINGLETON_KEY_VALUE = "default";
```

No product key, branding path, or app list is ever hardcoded in a Java
`if`/`switch` — every product-specific decision reads `products.yaml` (or
the seeded DB tables derived from it) at runtime.

### Frontend / shared placement (for the future branding wire-up)

Per-product branding constants (logo path, primary color, icon key) live
under `common/frontend/src/theme/products/<key>/product.theme.json` and
the Flutter mirror `common/mobile/lib/theme/products/<key>/` — **not**
inside any `apps/<id>/` folder, and not duplicated into
`common/frontend/src/theme/tokens.ts`. When the renderer is wired up
(doc 35 §5), it must read the bundled JSON for the packaged product, never
hardcode a color or logo path in a component.

---

## Optimization, Performance & Memory

### Performance

- `platform/catalog/products.yaml` is parsed **once per seed run at
  startup** (`ApplicationCatalogSeed.seed()` / `PlatformProductSeed.seed()`),
  never per-request — there is no runtime endpoint in this drop that
  re-parses it.
- `backfillProductKeys()` only issues an `UPDATE` when the stored
  `product_key` differs from the target value (`if
  (productKey.equals(stringValue(currentProductKey))) continue;`) — a
  restart with no catalog changes performs zero writes, only reads.
- The two singleton lookups (`platform_product`,
  `company_active_product`) are single-row, PK-indexed reads
  (`singleton_key = 'default'`) — O(1) regardless of how large either
  database grows.
- `package-product.sh`'s `awk`-based YAML extraction runs once per
  packaging invocation on the build host, never inside a request path or
  container startup.

### Memory

- `ApplicationCatalogSeed`/`PlatformProductSeed` hold no field-level
  mutable state between runs — each `seed()` call loads, processes, and
  discards its YAML document; nothing is cached in the bean itself.
- `PlatformProductSeed.readProductStampOrDefault()` opens the stamp file
  via `Files.newInputStream`, hands it directly to SnakeYAML, and returns
  before the method exits — no retained `InputStream` or byte buffer.
- `package-product.sh` streams `cp -a` per shared tree rather than
  building an in-memory file list first; large trees (e.g. `common/`) are
  copied by the OS, not buffered in the script.

### Optimization

- One authoritative file (`products.yaml`) feeds three consumers (DB
  product catalog seed, `application_catalog.product_key` backfill,
  packaging filter) — no second file to keep in sync, no drift between
  what gets packaged and what gets recorded in the database.
- `seedProductCatalog()` and `backfillProductKeys()` both use
  `GenericSqlBuilder`'s existing `selectFiltered`/`insert`/`update`
  helpers — no bespoke SQL string building, no new query paths to
  maintain alongside the rest of the schema-driven data layer.
- The packaging script computes `included`/`missing`/`excluded` with
  plain array membership checks (bash), no subprocess spawned per app —
  the entire filtering decision happens in-process before any `cp` runs.

---

## Dependencies / Out of Scope

- No admin UI or API exists yet to CRUD `product_catalog` or view which
  product a company belongs to — this drop is schema + seed + packaging
  only. A future `company-setup` extension can expose
  `GET/PUT /api/v1/opzhub/company-setup/product` for platform admins.
- `managemyid` and `smartaicampus` are placeholder entries with no
  `apps/<id>/` folders — they exist in `products.yaml` so the pattern for
  adding a product is demonstrated, but cannot be packaged until real app
  folders are built and listed.
- Branding layering (doc 35 §5) is a placeholder — `product.theme.json`
  files exist under `common/frontend/src/theme/products/<key>/` but no
  frontend code reads them yet.

---

## Standard Implementation Rules

See [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md). Unit tests for
`ApplicationCatalogSeed`'s new `seedProductCatalog`/`backfillProductKeys`
methods belong in `managemyopz-testing/01-unit/modules/apps/`.
