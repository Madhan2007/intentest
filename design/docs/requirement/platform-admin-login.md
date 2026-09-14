# Requirement: Platform Admin Login

**Module:** `identity` (extension)
**DB target:** `opzmain` (new table `platform_admin`)
**Access level:** Platform admin only — no company context
**Architecture ref:** [doc 35 §2](../architecture/35-platform-admin-and-product-catalog.md)
**Status:** Implemented (this drop) — see `modules/identity/backend/src/main/java/com/managemyopz/modules/identity/`

---

## What It Does

Allows a central operator to log in and manage companies/licenses without
ever being tied to a company. Reuses the existing `/login` endpoint — no
new route, no new screen. The identifier grammar already in place
distinguishes shapes by punctuation:

| Typed value | Meaning |
|-------------|--------|
| `ada@acme.com` | Tenant login by email |
| `acme/ada` | Tenant login by company/username |
| `platformadmin` (no `/`, no `@`) | Platform admin login — **new** |

A bare username was previously always rejected. It now resolves against a
separate, company-less identity store.

---

## DB Schema

**Table:** `platform_admin` — in `opzmain` (central, not per-company)

| Column | Type | Nullable | Default | Notes |
|--------|------|----------|---------|-------|
| `id` | uuid | No | `gen_random_uuid()` | PK |
| `username` | text | No | — | Unique. Validated with the same `^[a-z][a-z0-9._-]{0,63}$` pattern as tenant usernames |
| `display_name` | text | No | — | |
| `password_hash` | text | No | — | Argon2id, same `PasswordEncoder` bean as tenant users |
| `enabled` | boolean | No | `true` | |
| `created_at` | timestamptz | No | `now()` | |

No `company_id` column exists on this table — that is the entire point.

---

## Login Behavior

Reuses `POST /api/v1/opzhub/identity/login` (no new endpoint):

```json
{ "username": "platformadmin", "password": "..." }
```

**Response (200 OK):**

```json
{
  "ok": true,
  "data": {
    "user": { "id": "6f41...", "n": "Platform Administrator", "r": ["platform_admin"] },
    "a": { "company-setup": { "*": "vcua" } },
    "token": "c63b..."
  }
}
```

Note the `user` object has **no `c` key** — `IdentityController` already
omits it whenever `companyId` is null/blank, so no response-shape change
was required. Compare to a tenant login, which always includes
`"c": "<company_id>"`.

**Permission matrix:** exactly `{ "company-setup": { "*": "vcua" } }` —
nothing else. A platform admin cannot see or act on any tenant module.

**Failure:** identical generic `invalid_credentials` / 401 as tenant login
— a bare username that does not match any `platform_admin` row fails the
same way a bad tenant password does. No enumeration of valid usernames.

---

## Bootstrap

`DefaultPlatformAdminBootstrap` runs once at startup (mirrors
`DefaultAdminBootstrap`):

1. If `platform_admin` has any row, skip (never overwrite).
2. Otherwise create one row: `username = "platformadmin"`.
3. Password: `OPZHUB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD` env var if set,
   else a generated 24-character password written to
   `/etc/opzhub/bootstrap-platform-admin-password`.

Retrieve the generated password:

```bash
# Git Bash / MINGW64
MSYS_NO_PATHCONV=1 docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml \
    exec opzhub-be-app cat /etc/opzhub/bootstrap-platform-admin-password

# PowerShell
docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml `
    exec opzhub-be-app cat /etc/opzhub/bootstrap-platform-admin-password
```

To set a fixed password instead of a generated one, add to `infra/.env`
before first container start:

```dotenv
OPZHUB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD=<strong-unique-password>
```

---

## Business Rules

- A platform admin session's `companyId` is always `null`. `DataRouter`
  (when implemented) must treat a null `company_id` as "never switch
  pools — stay on OPZMAIN."
- Platform admin usernames and tenant usernames are independent namespaces
  (different tables, different databases) — `platformadmin` as a platform
  admin username does not collide with any company's `admin` tenant user.
- The bare-username lookup only runs when the identifier contains neither
  `/` nor `@`. Any identifier containing either character is never checked
  against `platform_admin`, so there is no ambiguity with tenant identifiers.
- Platform admin accounts are never routed to a company database — every
  action they take operates on `opzmain` only, through the `company-setup`
  module (create/update company, license, server, backend endpoint rows).

---

## Directory Placement

```
modules/identity/backend/src/main/java/com/managemyopz/modules/identity/
├── data/
│   ├── PlatformAdminRepository.java              — port, bound to OPZMAIN
│   └── DataClientPlatformAdminRepository.java    — Postgres implementation
└── application/
    ├── AuthService.java                          — extended: bare-username branch
    └── DefaultPlatformAdminBootstrap.java         — one-time bootstrap

modules/identity/db/commands/
├── identity.platform_admin.find_by_username.sql
├── identity.platform_admin.count.sql
└── identity.platform_admin.create.sql

modules/licensing/db/schema/
└── platform_admin.yaml                            — OPZMAIN schema (v1)
```

---

## Constants

Added to `IdentityApplicationConstants`:

```java
public static final String ROLE_PLATFORM_ADMIN = "platform_admin";
public static final String COMPANY_SETUP_MODULE_ID = "company-setup";
```

Added to `IdentityDataConstants`:

```java
public static final String PLATFORM_ADMIN_DATABASE_NAME = "OPZMAIN";
public static final String PLATFORM_ADMIN_FIND_BY_USERNAME_COMMAND = "identity.platform_admin.find_by_username";
public static final String PLATFORM_ADMIN_COUNT_COMMAND = "identity.platform_admin.count";
public static final String PLATFORM_ADMIN_CREATE_COMMAND = "identity.platform_admin.create";
```

---

## Optimization, Performance & Memory

### Performance

- The bare-username check (`USERNAME_PATTERN` regex + `platform_admin`
  lookup) is the **last** branch tried in `findUserByLoginIdentifier` —
  email (`@`) and company/username (`/`) are checked first with a cheap
  `contains()`, so no regex or DB round trip runs for the two far more
  common tenant login shapes.
- `platform_admin` is looked up by its unique `username` index — a single
  indexed point lookup, same cost profile as the existing `id_user`
  username lookup.
- No new cache entries are introduced; the session itself is cached
  exactly like a tenant session (`session:{token}` in Valkey, same TTL).

### Memory

- `DefaultPlatformAdminBootstrap` runs once per process lifetime
  (`ApplicationRunner`, not a scheduled task) — no recurring timer, no
  retained state after `bootstrap()` returns.
- The generated password is held only in a local `String` for the
  duration of `bootstrap()` and written once to disk; it is never cached,
  logged, or retained in a field.
- `PlatformAdminRepository`/`DataClientPlatformAdminRepository` hold no
  mutable state — every call opens a fresh, bounded `DataClient` query
  against the existing OPZMAIN connection pool (no new pool is created).

### Optimization

- Reuses the existing Argon2id `PasswordEncoder` bean — no second encoder
  instance, no duplicated cost-parameter tuning.
- Reuses the existing `/login` endpoint, cookie, and session-cache
  plumbing end to end — zero new HTTP routes, zero new
  `SessionAuthFilter`/`SessionAuthentication` code paths to maintain.
- `createIfAbsent` relies on `ON CONFLICT (username) DO NOTHING` at the
  SQL level (see `identity.platform_admin.create.sql`) rather than a
  check-then-insert round trip, so a concurrent boot race resolves in a
  single statement.

---

## Directory Structure Notes

`platform_admin` is schema-only inside `modules/licensing/db/schema/`
(licensing has `runtimes.backend: false` — it owns no Java, only OPZMAIN
table definitions consumed by other modules, the same pattern already
used for `company_information`, `server_details`, and `product_catalog`).
All Java that reads or writes it lives in `modules/identity/`, the actual
consumer — never inside `licensing` itself. This keeps the
common/application/module split intact: `licensing` is a **module**
(non-application-specific registry schema), `identity` is the **module**
that implements the login feature against it, and nothing here lives in
an application (`apps/<id>/`) folder because platform admin login is not
tied to any one sold application.

---

## Standard Implementation Rules

See [IMPLEMENTATION_RULES.md](IMPLEMENTATION_RULES.md). Unit tests for
`AuthService`'s bare-username branch belong in
`managemyopz-testing/01-unit/modules/identity/`.
