# Generic CRUD API — usage guide

One REST surface serves **every** entity declared under any enabled module's
`db/schema/*.yaml` — there is no per-entity controller, DTO, or hand-written SQL.
Adding a new table means adding a new schema file (see "Adding a new entity"
below); nothing else changes.

Implementation: `modules/crud` (`GenericCrudController` / `GenericCrudService` /
`GenericCrudRepository`), backed by the kernel schema engine in
`common/backend/src/main/java/com/managemyopz/kernel/data/schema/`.

For direct, in-process Java access (no HTTP, for module authors), see
[HELP-GENERIC-DATA-ACCESS.md](HELP-GENERIC-DATA-ACCESS.md) instead.

## Base URL and auth

```
POST/PUT/DELETE  /api/v1/opzhub/db/{entity}/{operation}
```

Every route requires an authenticated session (cookie `opzhub_session` or
`Authorization: Bearer <token>`) — same as the rest of the kernel API. There is
no per-feature permission check yet (a known, documented gap — anyone
authenticated can call any exposed entity's operations).

`{entity}` is the `entity:` value from a schema YAML — e.g. `company_information`,
`company_license`, `server_details`. An entity with `expose_generic_api: false`
(currently just identity's `id_user`) responds `404 not_found` on every
operation, indistinguishable from an entity that doesn't exist at all.

## Response envelope

Every response is wrapped the same way:

```json
{ "ok": true, "data": { ... }, "error": null, "correlation_id": "..." }
```

On failure:

```json
{ "ok": false, "data": null,
  "error": { "code": "validation_failed", "kind": "validation_failed",
             "msg": "Invalid request parameters.", "hint": null,
             "fields": { "company_name": "Field is required: company_name" } },
  "correlation_id": "..." }
```

| `error.code`          | HTTP status | Meaning |
|-----------------------|-------------|---------|
| `validation_failed`   | 400         | A field is missing/wrong-typed/too long, or an id isn't a valid value for the primary key's type |
| `not_found`           | 404         | Unknown entity, hidden entity, or no row with that id |
| `conflict`            | 409         | A unique constraint or foreign key was violated |
| `data_unavailable`    | 503         | The database is unreachable, or the entity's `database:` isn't wired for the current `db.type` |
| `internal_error`      | 500         | Unexpected — check server logs by `correlation_id` |

## Operations

### Create

```
POST /api/v1/opzhub/db/company_information/create
{ "row": { "company_name": "Technosprint info solutions", "license_code": "LIC-001", "server_name": "srv-1" } }
```
→ `201`, `data` is the full inserted row (including server-generated columns like `id`).

Omit a column entirely to let its schema-declared `default` apply (e.g. `id`'s
`gen_random_uuid()`). A column typed `jsonb` (e.g. `company_references`) must be
submitted as an **already-serialized JSON string**, not a native array/object:

```json
{ "row": { "company_name": "Technosprint", "company_references": "[\"technosprint\",\"technosprint.net\"]", "license_code": "LIC-001", "server_name": "srv-1" } }
```

### Read (by id)

```
POST /api/v1/opzhub/db/company_information/read
{ "id": "5b1c...-uuid" }
```
→ `200` with the row, or `404 not_found`.

### Filter (paged list)

```
POST /api/v1/opzhub/db/company_information/filter
{
  "filter": {
    "eq":    { "server_name": "srv-1" },
    "in":    { "license_code": ["LIC-001", "LIC-002"] },
    "range": { "created_at": { "gte": "2026-01-01T00:00:00Z" } },
    "sortBy": "company_name", "sortDir": "asc",
    "page": 0, "size": 20
  }
}
```
Every key in `eq`/`in`/`range`/`sortBy` must be a real column on the entity — an
unknown field is rejected before any query runs. `size` is clamped to 1–100.
Omit `filter` entirely (`{}`) for an unfiltered first page.

→ `200`:
```json
{ "items": [ { ... }, { ... } ], "page": 0, "size": 20, "totalItems": 2, "totalPages": 1, "hasMore": false }
```

### Count

Same `{ "filter": {...} }` body as filter, posted to `/count`:
```
POST /api/v1/opzhub/db/company_information/count
```
→ `200`, `data: { "total": 2 }`.

### Update (partial patch)

```
PUT /api/v1/opzhub/db/company_information/update
{ "id": "5b1c...-uuid", "patch": { "company_name": "Technosprint Info Solutions Pvt Ltd" } }
```
Only the keys present in `patch` are changed — everything else on the row is
left alone. → `200` with the full updated row, or `404 not_found`.

### Delete

```
DELETE /api/v1/opzhub/db/company_information/delete
{ "id": "5b1c...-uuid" }
```
→ `200`, `data: { "deleted": true, "id": "5b1c...-uuid" }`, or `404 not_found`.
A row still referenced by a foreign key (e.g. deleting a `company_license` row
still linked from `company_information`) returns `409 conflict`.

### Bulk (all-or-nothing)

One request, one database transaction — if any row in the batch fails, the
**entire batch rolls back**, nothing is committed. There is no partial-success
response shape.

```
POST /api/v1/opzhub/db/company_information/bulk
{ "op": "create", "rows": [ { "company_name": "A", "license_code": "L1", "server_name": "S1" },
                             { "company_name": "B", "license_code": "L2", "server_name": "S2" } ] }
```
`op` is one of:
- `"create"` with `"rows": [ {...}, {...} ]`
- `"update"` with `"patches": [ { "id": "...", "patch": {...} }, ... ]`
- `"delete"` with `"ids": [ "...", "..." ]`

→ `200`, `data: { "op": "create", "count": 2 }` on full success, or the usual
error envelope (400/404/409) with nothing committed on any failure.

## Adding a new entity

Drop a file at `modules/<your-module>/db/schema/<table>.yaml` (see the three
existing examples under `modules/licensing/db/schema/`), then restart the app —
`SchemaReconciler` creates the table automatically (additive only: it will
later add new columns/indexes to an existing table, but **never** drops or
alters one, so evolving the schema never loses data).

```yaml
entity: my_table            # used in the URL: /api/v1/opzhub/db/my_table/...
table: my_table             # physical Postgres table name (defaults to entity)
version: 1
database: OPZMAIN           # omit to use the default connection; see the databases: block in platform.yaml
expose_generic_api: true    # omit for true; set false to hide from every /db/{entity}/* route (e.g. secrets tables)
columns:
  - { name: id, type: uuid, primary_key: true, nullable: false, default: gen_random_uuid() }
  - { name: name, type: text, nullable: false, max_length: 200 }
  - { name: notes, type: text, nullable: true }
indexes:
  - { name: idx_my_table_name, columns: [name], unique: true }
foreign_keys:
  - { column: parent_id, references_table: other_table, references_column: id, on_delete: restrict, enforce: postgres }
```

Supported `type` values: `uuid`, `text`, `integer`, `bigint`, `decimal`,
`boolean`, `timestamptz`, `date`, `jsonb`, `text_array` (`text_array` is DDL-only
today — see HELP-GENERIC-DATA-ACCESS.md if you need to write to one). A `default`
value must be one of `now()`, `gen_random_uuid()`, `true`/`false`, an integer, or
a short quoted string — anything else is rejected at boot as a safety measure
(schema files can never become a SQL-injection vector).

You'll also need the module registered like any other (see `modules/licensing`
for the minimal "schema-only, no Java" shape, or `modules/crud`'s own
`pom.xml` resource entry pattern) and listed in `platform.yaml`'s
`modules.enabled`.
