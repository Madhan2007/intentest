# 20 — Licensing, site vs central deploy, company login

ManageMyOpz is **licensed**. The **license server is centralized**. **The same FE and BE code** runs everywhere. What changes per company is **routing**: where the GUI is, and **where Postgres lives**.

## 1. Topologies (same binaries)

| Topology | FE / BE process | Customer business DB | Typical YAML |
| -------- | --------------- | -------------------- | ------------ |
| **Site (full stack)** | Customer appliance | Same appliance | `deploy.mode: site` |
| **Central + hub DB** | Shared hub cluster | **Same** central Postgres (`company_id` / schema) | `deploy.mode: central` + company `data: hub` |
| **Central + customer DB** | Shared hub cluster | **Only** the DB at the customer (TLS from hub) | `deploy.mode: central` + company `data: customer` |

Central therefore has **two subtypes**. Users still open the **central URL**, type `acme/ada`, get that company’s logo, then the hub **routes**:

- HTTP/WSS stay on the **central FE/BE** (same code).
- `DataClient` opens the **mapped Postgres** (hub pool **or** that customer’s host).

```
  Browser / Flutter ──► Central GUI + API (one codebase)
                              │
                              │ DataClient (per company)
              ┌───────────────┴───────────────┐
              ▼                               ▼
     Hub Postgres                      Customer Postgres
     (data: hub)                       (data: customer)
```

Full **site** mode is the other product shape: FE, BE, **and** DB all on the customer box (login may skip company prefix). License heartbeat still goes to the central license server.

## 2. Company identifier at login (central URL)

On the **centralized environment URL**, the first field accepts:

| Typed | Parsed |
| ----- | ------ |
| `acme/ada` | company `acme`, user `ada` |
| `ada@acme` | company `acme`, user `ada` |
| `acme/ada@corp` | company `acme`, user `ada@corp` (user may contain `@` only in the `company/user` form) |

Slug: lowercase `[a-z0-9][a-z0-9-]{1,63}`. Unknown or unlicensed company → generic error (rate-limited; do not list all companies).

**Site appliances** (`deploy.mode: site`) skip company prefix; they have one `deploy.company` and one local DB. They still **live-check** the instance license and **re-check on password** against the same central license server (§2.2–2.3).

### 2.1 Live check while typing

The login field calls the hub **as the user types** (debounced). The hub **always asks the centralized license server** — it must not decide “licensed” from a stale local row alone.

| Client | Rule |
| ------ | ---- |
| When | Parsed **company slug** is ≥ 2 chars (`acme/…` or `…@acme`). Incomplete `a` / `ac` → no request. |
| Debounce | `license.live_ms` (default **400**). Cancel in-flight if the field changes. One in-flight per tab. |
| Endpoint | `POST /api/v1/opzhub/license/resolve` `{ "q": "acme/ada", "live": true }` |
| Rate | Per IP + prefix; 429 with generic body. Do not autocomplete other companies. |

Hub Java (`LicenseClient`):

```
POST {license.hub_url}/api/v1/license/validate
{
  "co": "acme",
  "instance": "${OPZHUB_INSTANCE_ID}",
  "phase": "live",
  "method": null
}
```

Never send username password, face image, or fingerprint to the license server.

Live response to the **browser** (same tiny shape as full resolve; `live: true` may omit `gui` until slug+user parse is complete):

```json
{
  "ok": true,
  "co": "acme",
  "u": "ada",
  "n": "Acme Ltd",
  "logo": "/brand/acme.svg",
  "gui": "https://hub.example.com",
  "api": "https://hub.example.com",
  "auth": ["password", "oauth2"],
  "st": "ok",
  "skin": "lite"
}
```

`st`: `ok` \| `wait` \| `bad`. Prod `bad` is generic (no “expired” vs “unknown”). `auth` is the **license entitlement ∩ local YAML ∩ pack** — if `password` is not in this list, **do not show** the password field.

Hub may cache `validate` per slug for `license.validate_ttl_seconds` (short, e.g. 45s). Live UI may use cache; **password submit must not** skip a fresh check if TTL elapsed (§2.3).

### 2.2 Resolve (blur / submit of company field)

Same `POST /api/v1/opzhub/license/resolve` with `live: false` (default). Hub calls `phase: "resolve"` on the license server. Full `gui` / `api` / `auth` as above.

For **central** subtypes, `gui` / `api` are the **hub** (same FE/BE). The browser does **not** receive DB hosts. Routing to hub vs customer Postgres happens **inside** the BE `DataClient`.

For **full site** companies, `gui` / `api` are the **customer HTTPS origin**; the SPA navigates there for password (that box has its own BE+DB). **That box** then live-checks and password-checks the **same** license server.

| Field | Purpose |
| ----- | ------- |
| `logo` / `n` | Swap login screen branding **before** password |
| `gui` / `api` | Browser origin: hub (both central subtypes) or site box (full site) |
| `auth` | Methods the **license server** allows for this company (short list) |
| `st` | Live/resolve validity (`ok` / `wait` / `bad`) |
| `skin` | Optional `lite` \| `rich` chrome for this company; omit = site `gui.mode` |

**Not** in this response: DB URLs, passwords, license blobs, RBAC matrix, other companies.

### 2.3 Password (and every method) — validity on the license server

Typing or submitting a **password does not** send the secret to the license server. The **method type** `password` (and `oauth2` / `face` / `fingerprint`) **is** checked there.

Before Argon2id (or OAuth start, or bio verify):

```
POST {license.hub_url}/api/v1/license/validate
{
  "co": "acme",
  "instance": "${OPZHUB_INSTANCE_ID}",
  "phase": "login",
  "method": "password"
}
```

| Result | Login |
| ------ | ----- |
| `ok` + `auth` contains `password` | Compare hash locally; issue session |
| `ok` but `password` not entitled | Same generic fail as bad password (no leak) |
| expired / suspended / unknown | Generic fail; do not verify hash (timing: still dummy-hash) |
| license server down | Prod hub: **fail closed**. Site: signed `license.lease` only within `grace_days` |

The same `phase: "login"` + `method` runs for face, fingerprint, and OAuth2 start. YAML `security.auth.methods` is a **local cap** only; the license server is the authority.

When the **password field is shown or focused** (user starts typing the secret), the client may call resolve again with `live: true` or:

```
POST /api/v1/opzhub/license/preflight
{ "co": "acme", "method": "password" }
```

Hub/site maps this to license `phase: "login"` + `method: "password"`. Response `{ "ok": true }` or generic fail — **no password in the body**. If `ok` is false, hide/disable the password box. Submit still runs §2.3 even if preflight succeeded.

`license.validate_on_login: true` is required in prod (`opzhubctl doctor`).

## 3. Company directory (central DB)

Table conceptually `lic_company`:

| Column | Meaning |
| ------ | ------- |
| `slug` | `acme` |
| `display_name` / `logo_uri` | Branding |
| `stack` | `site` \| `central` |
| `data` | `hub` \| `customer` — **only when `stack=central`** |
| `gui_origin` / `api_origin` | Hub URL if central FE; customer URL if full site |
| `db_host` / `db_name` / `db_user` | Set when `data=customer`; secrets in hub vault, **not** in resolve JSON |
| `license_id` / `status` | License row; `active` \| `suspended` \| `expired` |

| `stack` | `data` | FE/BE | Postgres |
| ------- | ------ | ----- | -------- |
| `site` | (local) | Customer box | Customer box |
| `central` | `hub` | Hub | Hub (shared cluster, tenant key) |
| `central` | `customer` | Hub (same code) | Customer site only |

## 3.1 Data routing (same FE/BE code)

Feature modules **never** branch on topology. They call `DataClient` as today ([07](07-data-cache-client-server.md)).

Kernel **DataRouter** (central BE only):

1. Session has `company_id` after login.
2. Load `lic_company` for that slug (cached, short TTL).
3. If `data=hub` → use the hub Postgres pool (`SET app.company_id` / RLS).
4. If `data=customer` → use a **per-company pool** to `db_host` over TLS (verify-full). Idle pools evicted.

YAML on the **hub** process:

```yaml
deploy:
  mode: central

db:
  type: postgres
  router: company                 # off on site appliances
```

Site process: `deploy.mode: site`, `db.router: off`, single `db.postgres` block — no router.

Cache/Valkey and Nginx stay on the **hub** for both central subtypes. Only **business Postgres** may sit at the customer.

Migrations / hourly backup: `data=hub` → hub jobs. `data=customer` → hub `opzhubctl migrate` and backup **target that company’s DSN** (or the customer runs dump on-box and the hub only migrates). Same migrator code; DSN from the directory.

Customer Postgres must allow the **hub NAT/VPN** only; never `0.0.0.0/0` for `5432`.

## 4. Centralized license server

All deployments (site and hub) talk to **one** license service (`license.hub_url`).

| Check | Rule |
| ----- | ---- |
| Issue | License lists **company**, **expiry**, **allowed application ids**, and **allowed login methods** (`password`, `oauth2`, `face`, `fingerprint`) |
| Live type | Hub `resolve` with `live: true` → license `phase: live` (§2.1) |
| Password / login | Hub/site `phase: login` + `method` **before** local credential check (§2.3). Secret never sent. |
| Site heartbeat | Cron (`opzhub-run-cron license-beat`) posts instance id + pack fingerprint; fail closed after `grace_days` if unreachable (YAML) |
| Pack vs license | `opzhubctl doctor` / boot: enabled modules must be a **subset** of licensed apps. Extra folders fail. |
| Central login | Resolve and login refuse `status != active` or expired license |

Site boxes do **not** become a second license authority. They cache the last signed lease in `/etc/opzhub/license.lease` (kept across upgrades).

## 5. YAML

```yaml
# Site appliance (FE+BE+DB on customer box)
deploy:
  mode: site
  company: acme
db:
  router: off

# Hub process (same FE/BE image)
deploy:
  mode: central
db:
  type: postgres
  router: company                 # DataRouter: hub vs customer DSN per slug
```

Hub omits `deploy.company`. Each company row sets `data: hub` or `data: customer`.

License (all topologies):

```yaml
license:
  hub_url: https://license.managemyopz.example
  instance_id: ${OPZHUB_INSTANCE_ID}
  grace_days: 7
  lease_path: /etc/opzhub/license.lease
  live_ms: 400                      # login-field debounce
  validate_on_login: true           # required in prod
  validate_ttl_seconds: 45          # live cache only; login re-checks if stale
```

## 6. GUI routing after resolve

1. User opens **central** URL → generic hub login (no password box yet).
2. Types `acme/ada` → **live** `resolve` (debounced) against the **central license server** → logo + `auth[]`.
3. Password field appears **only if** `auth` contains `password`. Focus/first keystroke → `preflight` (`method: password`) on the same license server.
4. If company is **central** (either DB subtype): stay on hub origin; password / OAuth2 / face / fingerprint hit hub BE (license `phase: login` first); BE `DataRouter` picks hub or customer Postgres.
5. If company is **full site**: `gui` is the customer URL; SPA/Flutter go there; that box uses local DB (`router: off`) and the **same** license server for live + password checks.
6. Access matrix stays compact ([18](18-identity-rbac-abac-oauth2.md)).

Flutter: persist `origin` + `co`; HTTP/WSS to `gui`. Never persist DB hosts.

## 7. What must not happen

- Posting the **password secret** to the license server (only `co` + `method` + instance).
- Showing a password box from YAML when the license `auth` list omits `password`.
- Calling `resolve` on every keystroke with no debounce / no cancel of in-flight.
- Treating local `lic_company.status` as enough for login without `phase: login`.
- Putting customer `db_host` or DB passwords in the **browser** resolve payload.
- Feature code with `if (central) … else if (customerDb)`.
- Opening customer `5432` to the whole internet.
- Posting the password to the hub when `gui` is a **full-site** URL.
- Returning every tenant’s mapping to the browser.
- Enabling modules the license does not list.
- License secrets in the release zip.
