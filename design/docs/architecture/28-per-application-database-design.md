# 28 — Per-Application Database & Cross-Application Data Access

This document is **design only**. It formalizes two rules that apply to
**every** sold application under `apps/<name>`, not only
`manage-my-people` — first applied there
([27](27-people-domain-design.md)) but stated here generally so the next
sold app follows the same pattern without re-deriving it.

## 1. Background: logical databases today

The `database:` field on every `db/schema/*.yaml` file
([02](02-repository-folder-structure.md) §8, [07](07-data-cache-client-server.md) §4.1)
names a **logical Postgres database** — a real `CREATE DATABASE`, not a
schema — that `PostgresDataServer` keeps its own connection pool for. This
was never written down as a catalog before this document; it existed only
as convention across schema files. Today there are three:

| Logical DB | Owns | Example tables |
|---|---|---|
| `OPZMAIN` | Core company/platform registry | `company_information`, `server_details`, `backend_endpoint`, `backup_job`, `maintenance_window` |
| `OPZUSER` | Identity, RBAC, per-user preferences | `id_user`, `id_role`, `id_user_role`, `id_role_permission`, `id_user_permission`, `id_field_definition`, `user_settings` |
| `OPZHUB` | App catalog and per-company install/license state | `application_catalog`, `company_application` |

All three are **platform-level** — every installed pack needs them
regardless of which sold apps are bought. `doc 07 §4.1` covers connection
pooling per `db.type`; this document extends it to say `PostgresDataServer`
opens one pool **per distinct `database` value** declared across every
module/app present on disk, not one pool per `db.type`.

## 2. Rule: one logical database per sold application

Every `apps/<name>` gets its **own** logical database, named `OPZ<SHORT>`
where `<SHORT>` is the app's catalog `app_key` with the `manage-my-`
prefix stripped and upper-cased:

| App | `app_key` | Logical DB |
|---|---|---|
| Manage My People | `manage-my-people` | `OPZPEOPLE` |
| Manage My Desk | `manage-my-desk` | `OPZDESK` |
| Manage My Data | `manage-my-data` | `OPZDATA` |
| Manage My Finance | `manage-my-finance` | `OPZFINANCE` |
| Manage My Market (example — any future marketplace-style app) | `manage-my-market` | `OPZMARKET` |
| *(next sold app)* | `manage-my-<x>` | `OPZ<X>` |

**Why:** the platform's core principle is "folder is the plugin"
([00](00-system-overview.md) §2) — presence of `apps/<name>` on disk is the
source of truth for whether a feature exists. That principle stopped at
code before this rule: uninstalling `manage-my-people` still left its
tables inside a shared `OPZMAIN`, permanently, indistinguishable from
platform registry data. One database per app means:

- Uninstall/offboard a sold app by dropping exactly its database — no
  risk to `OPZMAIN`/`OPZUSER`/`OPZHUB` or to any other app's data.
- A 50+-app vendor catalog does not compound into one unbounded `OPZMAIN`
  as more apps are built; each app's growth, backup schedule, and
  migration history are independent ([19](19-db-backup-migrate.md) applies
  per logical database, not once for the whole platform).
- A customer pack with 3 bought apps provisions 3 app databases + the 3
  platform databases — never the other 47 catalog apps' databases,
  matching [10](10-solution-composition.md)'s subset-only packaging.

**How declared:** exactly like today — every entity in
`apps/<name>/db/schema/*.yaml` sets `database: OPZ<SHORT>`. No new YAML
key at the module/app manifest level is required; the schema files are
already the source of truth `SchemaRegistry` reads at boot.

**Tables that must stay on a platform database:** a sold app never
declares its own copy of `company_information`, identity, or app-catalog
tables — those stay `OPZMAIN`/`OPZUSER`/`OPZHUB` as today. Only the app's
**own** domain tables move into its dedicated database.

## 3. Rule: cross-application data access without duplication

Postgres cannot enforce a `FOREIGN KEY` across two databases — this was
already true for `company_id → company_information` (`OPZMAIN`) from
`OPZUSER`/`OPZHUB` tables, documented ad hoc in schema comments (e.g.
`id_user.yaml`, `company_application.yaml`: *"Cross-database: ... Postgres
cannot create that constraint; the kernel enforces it at write time
(APP)"*). This section names that existing pattern and extends it to the
many-databases model from §2, so the next app doesn't reinvent it and,
critically, does **not** fall back to copying the referenced data locally
"to make the join easy."

### 3.1 The rule

When app A needs a field that app B owns:

1. **Never duplicate the column into A's database.** Store only the
   foreign id (+ the owning app's token if ambiguous — same shape as the
   existing "optional module" logical FK in
   [22](22-common-fields-forms-fk.md) §6.2). No `manage-my-hra` table gets
   its own `person_name` copy of `ppl_person.display_name`.
2. **No Postgres `REFERENCES` across databases** — physically impossible
   and not attempted.
3. **Reads go through a second `DataClient` query against B's own logical
   database, using B's own registered named commands** — never raw SQL
   reaching into another app's tables from app A's `db/commands/`. This
   is the same discipline as doc 02 §11's "module → module is forbidden,
   use kernel ports," applied at the data layer: app A calls app B's
   published command name (e.g. `manage-my-people.person.find_by_ids`),
   the same way it would call a kernel port — it does not know or care
   that this executes as a second Postgres connection instead of an HTTP
   call.
4. **Writes validate existence via the same application-layer check
   already used for `company_id`** — a service-layer lookup before
   insert/update, mapped to the existing `Conflict` / `err: fk` error
   shape ([07](07-data-cache-client-server.md) §11), not a DB constraint.
5. **Batch, don't loop.** Resolve a list of foreign ids with one
   `find_by_ids` call against B, never one query per row (the universal
   N+1 rule, [Rule 7](../requirement/IMPLEMENTATION_RULES.md#rule-7--performance-and-memory-universal),
   applies across databases exactly as it does within one).
6. **Cache read-mostly cross-app lookups** in `CacheClient`, TTL +
   explicit invalidation from the **owning** app's write path (e.g. app B
   evicts `people:person:{id}` on every `ppl_person` update; app A's
   cross-app read checks that cache before calling B again). Never an
   unbounded, un-invalidated local cache of another app's data — that is
   duplication with extra steps.

### 3.2 Concrete example — this platform's first two cases

- `ppl_person.company_id → company_information.id` (`OPZPEOPLE` →
  `OPZMAIN`): existing pattern, now formalized under this rule instead of
  being a one-off comment.
- `ppl_person.linked_user_id → id_user.id` (`OPZPEOPLE` → `OPZUSER`,
  nullable, optional): the *second* instance of this rule, introduced by
  [27](27-people-domain-design.md) — a person may optionally be linked to
  a login account; `manage-my-people` never copies `id_user` rows, it
  resolves the link live through identity's registered commands when
  needed (e.g. `GET /persons/me`), and caches the resolution per session
  rather than per request.

### 3.3 What this is not

This is **not** a distributed-transaction or two-phase-commit mechanism.
A write to app A and a related write to app B are **two separate ACID
transactions**, each local to its own database — exactly as a write to
`ppl_person` and a write to `company_application` (different databases
already, `OPZPEOPLE`/`OPZHUB`) are today. Design any cross-app write flow
to tolerate the second write failing after the first succeeds (retry the
second, or make it idempotent) — do not assume both commit together.

## 4. What must not happen

- A sold app's business tables declared under `database: OPZMAIN` "to
  keep it simple" — every new sold app gets its own `OPZ<SHORT>`.
- Copying another app's column into your own table so a local join is
  easier ("just add `person_name` to the enrollment table").
- Raw SQL in app A's `db/commands/` referencing app B's table names
  directly.
- A cross-app Postgres `FOREIGN KEY`.
- An un-invalidated local cache of another app's data used as a
  substitute for a live cross-app read.
- Assuming a write to A and the related write to B are one transaction.
