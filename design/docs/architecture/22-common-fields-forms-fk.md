# 22 — Common fields, form policy from BE, foreign keys

The **field kit and access apply live in the kernel** ([03](03-frontend-design.md)). Sold applications only **name a form id** and bind routes. They do not fork checkboxes, radios, text rules, or `if (app === "hr")` permission code.

**Runtime truth for mandatory / optional / free-text rules / effective edit mode is the Java engine**, evaluated with RBAC+ABAC ([18](18-identity-rbac-abac-oauth2.md)). Module YAML may list **catalog defaults**; the client never treats those as the last word.

This document is **design only**.

## 1. Why this is kernel, not per application

| Concern | Owner | Must not |
| ------- | ----- | -------- |
| Checkbox, radio, free text, single select, multi select | `common/frontend` + `common/mobile` | Copy-paste in `modules/hr` |
| Required / optional / pattern / length | BE `FormEnvelope` | Hardcode `required: true` only in the SPA |
| Hide / view / edit a field or button | Kernel `AccessApply` + envelope `mode` | `if (feature === "emp")` in kernel |
| Foreign key pickers | Kernel `lookup` / `lookup-multi` + BE `fk` | Raw `<select>` of ids in a module |
| Import / export / bulk CUD | Kernel collection toolbar + BE `io` / `bulk` flags | Per-app CSV pages or custom “Excel upload” |
| DB referential integrity | Postgres FKs where the parent table is **always** present | FK to a **optional** module table |

Kernel code mentions **generic keys** (`app`, `feature`, `letter`, `form_id`, `field`). It never imports `modules/hr` and never switches on sold app ids.

## 2. Primitive controls (must exist in common)

Same registry keys on web and Flutter ([03](03-frontend-design.md) §4). Modules bind **keys**, not React/Dart widgets.

| Key | Control | Use |
| --- | ------- | --- |
| `boolean` | Checkbox **or** Switch (`widget: checkbox \| switch`) | One flag |
| `checkbox-group` | CheckboxGroup | Several independent flags |
| `radio` | Radio | Exclusive **small** enum (2–7 options) |
| `select` | single dropdown | Exclusive **closed** enum (or short static list from BE) |
| `multiselect` | multi dropdown | Several values from a **closed** list |
| `text` | single-line free text | Codes, names |
| `textarea` | multi-line free text | Memos |
| `lookup` | async single FK | Parent row in another resource |
| `lookup-multi` | async multi FK | Several parents (allocations, tags-as-ids) |

Closed lists (`radio` / `select` / `multiselect` / `checkbox-group`) get `options[]` from the **FormEnvelope**, not from a hardcoded HR enum in the kernel.

Open lists (items, accounts, parties) use `lookup*`, never a 10k-option `<select>`.

## 3. FormEnvelope — BE is the source of truth

Opening a screen (or focusing a form) loads:

```
GET /api/v1/opzhub/forms/{form_id}
    ?mode=create|edit|view
    &id={resource_id}          # edit/view; omit on create
```

`form_id` is an **opaque string** registered by a module (`hr.emp.edit`). The kernel HTTP client does not parse it as `hr`.

```json
{
  "ok": true,
  "data": {
    "form": "hr.emp.edit",
    "ver": 3,
    "access": { "app": "hr", "f": "emp", "x": "u" },
    "fields": [
      {
        "n": "code",
        "t": "text",
        "req": true,
        "mode": "edit",
        "rules": { "min": 2, "max": 32, "pat": "^[A-Z0-9._-]+$", "trim": true, "case": "upper" }
      },
      {
        "n": "dept_id",
        "t": "lookup",
        "req": true,
        "mode": "edit",
        "fk": { "res": "master-data.dept", "v": "id", "d": "name" }
      },
      {
        "n": "status",
        "t": "radio",
        "req": true,
        "mode": "edit",
        "opts": [ { "v": "A", "l": "Active" }, { "v": "I", "l": "Inactive" } ]
      },
      {
        "n": "flags",
        "t": "checkbox-group",
        "req": false,
        "mode": "edit",
        "opts": [ { "v": "expat", "l": "Expat" } ]
      },
      {
        "n": "skills",
        "t": "multiselect",
        "req": false,
        "mode": "edit",
        "opts": [ { "v": "java", "l": "Java" } ]
      },
      {
        "n": "notes",
        "t": "textarea",
        "req": false,
        "mode": "edit",
        "rules": { "max": 2000, "deny": ["html"] }
      }
    ]
  }
}
```

| Field | Meaning |
| ----- | ------- |
| `n` | Field name (wire + form state) |
| `t` | Registry key (§2 / [03](03-frontend-design.md) §4) |
| `req` | **Mandatory** (`true`) or **optional** (`false`). From BE after policy + ABAC. |
| `mode` | `edit` \| `view` \| `hide` — **effective**, after RBAC+ABAC + field policy |
| `rules` | Free-text / numeric constraints (client convenience; **server re-checks**) |
| `opts` | Closed-list labels. i18n: `l` may be a key the module dictionary already has |
| `fk` | Foreign-key lookup (single or multi). Kernel LookupField only. |
| `access` | This **screen’s** grant. Kernel uses `has(access.app, access.f, letter)` — no app literals. |

Login JSON still has only compact `a` ([18](18-identity-rbac-abac-oauth2.md) §4). Do **not** put every field’s `req`/`rules` on the session. Fetch per form (cache by `form`+`ver`+user roles in `CacheClient`, invalidate on grant change).

`POST /api/v1/opzhub/forms/{form_id}/validate` (optional, same body as save) returns field errors without writing. Save endpoints always run the same validator.

## 4. Mandatory, optional, free-text rules

### 4.1 Catalog vs effective

| Layer | What | Who writes |
| ----- | ---- | ---------- |
| Module `forms/*.yaml` | Default `req`, `rules`, `t`, `fk` | Application author |
| Identity field policy (optional) | Override `req` / `mode` per role or ABAC | Admin / identity |
| **FormEnvelope** | Effective `req`, `mode`, `rules`, `opts`, `fk` | Java `FormPort` |

Client algorithm (kernel, generic):

1. If `mode=hide` → do not render, do not POST the field.
2. If `mode=view` → display control, no edit.
3. If `mode=edit` and `req=true` → empty / null fails submit (and BE 422).
4. If `req=false` → empty allowed; if non-empty, `rules` still apply.

A field may be **optional for role A and mandatory for role B**. Only the envelope says which.

### 4.2 `rules` object (free text and shared)

All keys optional. Unknown keys ignored by old clients; server still enforces.

| Key | Applies | Meaning |
| --- | ------- | ------- |
| `min` / `max` | text, textarea, number | Length or numeric bounds |
| `pat` | text | Full-match regex (RE2 subset; no catastrophic backtracking) |
| `trim` | text | Trim before validate/save |
| `case` | text | `upper` \| `lower` \| `none` (normalize on blur + on server) |
| `charset` | text | `ascii` \| `latin` \| `any` |
| `fmt` | text | `email` \| `phone` \| `url` \| `code` (kernel parsers) |
| `deny` | textarea / richtext | `html`, `url` — strip or reject |
| `scale` | decimal / money | Digits after radix (from currency master when money) |
| `min_sel` / `max_sel` | multiselect, checkbox-group, lookup-multi | Cardinality |

Client validation is **UX only**. Java rejects the same violations with a field-error list (`{ "n": "code", "err": "pat" }`). Never trust the browser for money, uniqueness, or FK existence.

### 4.3 Depends-on (still from BE)

Envelope may include:

```
"when": [ { "n": "status", "eq": "I", "then": { "req": false, "mode": "hide" } } ]
```

Kernel `dependsOn` applies this **generically**. Modules do not write `if (status === 'I')`.

## 5. RBAC / ABAC apply in common (no application names)

### 5.1 Kernel API (web + Flutter)

```
access.has(app, feature, letter)     # session.a only
access.canScreen(envelope.access, letter)
field.mode                           # already computed by BE — prefer this on forms
```

`permissions.ts` / `access_apply.dart`:

- Menu / route: `has(item.app, item.feature, "v")`. `item.app` comes from **module register()**, not from a kernel table of apps.
- Button `create` / `save` / `delete` / `approve` / import / export / bulk: map to letters `c` `u` `d` `a` / `v` via **slot meta** (`data-letter`), never `"hr.emp"`. Import/export/bulk visibility still follows envelope `io`/`bulk`.
- Fields: use envelope `mode` / `req`. Do not re-derive field ACL in the client from letters unless the envelope is missing (then: no `v` → hide form; `v` without `u` → all `view`; `u` → `edit` until envelope arrives).

### 5.2 What the kernel must never contain

```
// forbidden in common/frontend and common/mobile
if (app === "hr" || feature === "emp") { ... }
if (formId.startsWith("hr.")) { ... }
```

Sold apps may pass `{ app, feature }` **as data** when they `register` a route. Kernel only stores and tests those strings.

### 5.3 Server

`FormPort.build(form_id, user, resource)`:

1. Resolve catalog YAML for `form_id` (module resource). Unknown form → 404 (do not leak other packs).
2. `AccessPort.require(app, feature, letter for mode)` — create needs `c`, edit `u`, view `v`.
3. ABAC on the resource (row). Deny → 403.
4. For each field: start from catalog `req`/`rules`/`t`; apply field-policy rows (still `app`+`feature`+field name, not a new matrix letter); set `mode`.
5. Strip `hide` fields from the JSON **or** send `mode: hide` (prefer omit in prod to keep payload small).
6. Resolve `opts` and confirm `fk.res` module is **installed**. If the parent app is not in the pack, omit the field or mark `mode: hide`.

Save path: same `FormPort.validate(form_id, body, user, resource)` then module command. Unique / FK failures map to `Conflict` / field `err: fk`.

## 6. Foreign keys

### 6.1 When to use a **Postgres FOREIGN KEY**

Use a real `REFERENCES` constraint when **all** of these hold:

- Parent table is in **kernel** or in a module listed in this module’s `requires` (always on disk if this module is).
- Delete/update rule is well-defined (`RESTRICT` default; `CASCADE` only for owned children in the **same** module).
- The pair is tenant-safe (`tenant_id` in the key or RLS so company A cannot point at company B’s parent).

**Required places (v1):**

| Child | Parent | On delete |
| ----- | ------ | --------- |
| `id_grant.user_id` | `id_user` | RESTRICT |
| `id_grant.role_id` | `id_role` | RESTRICT |
| `id_role` tenant | kernel org / tenant | RESTRICT |
| Module row `created_by` | `id_user` | RESTRICT |
| `ledger_line.account_id` | CoA (ledger) | RESTRICT |
| `ledger_header.currency_id` | currency master (`master-data` required by ledger) | RESTRICT |
| `inv_move.item_id` | item (`master-data` or inventory) | RESTRICT |
| `inv_move.location_id` | location | RESTRICT |
| Line → header in the **same** module | header PK | CASCADE |
| OCR job → document | `documents` if `ocr` requires it | RESTRICT |

Identity and money graphs **always** use engine FKs. `db.required_capabilities` includes `relational_constraints` ([07](07-data-cache-client-server.md)).

### 6.2 When **not** to use a Postgres FK (logical FK)

If the parent lives in an **optional** sold app (HR employee id on a ticket, OCR doc on an HR file):

- Store the id + `app` / resource token.
- Validate in the application service: parent exists **if** that module is loaded; else reject or allow null per catalog.
- No `REFERENCES modules_hr_emp` from ticketing — removing `modules/hr` must not break migrate.

`FormEnvelope.fk` still describes the picker. Lookup API 404s if the parent module is absent; kernel shows empty + optional hint.

### 6.3 UI FK (kernel only)

```
fk: {
  res: "master-data.dept",     # lookup route token, not a SQL table name
  v: "id",
  d: "name",
  multi: false,
  q: { active: true }          # default filters; ABAC applied on the lookup API
}
```

- Single: `t: lookup`. Multi: `t: lookup-multi` (`fk.multi: true`).
- Search: `GET /api/v1/opzhub/lookup/{res}?q=` through kernel `LookupField`. Module implements `LookupPort` for `res`; kernel has **no** `if (res === "hr.emp")`.
- Display: show `d`, submit `v` only.
- Orphan id (parent deleted, logical FK): view shows `#id` + stale; edit cannot save until the user picks a live parent or clears if `req=false`.

Memory DataServer ([07](07-data-cache-client-server.md)) **emulates** FK checks used by unit tests; it does not need SQL `REFERENCES`.

### 6.4 Migrations

```
-- same module or requires: real FK
ALTER TABLE hr_emp
  ADD CONSTRAINT hr_emp_dept_fk
  FOREIGN KEY (tenant_id, dept_id)
  REFERENCES md_dept (tenant_id, id)
  ON DELETE RESTRICT;

-- optional other app: column only, no CONSTRAINT
-- tkt_inc.hr_emp_id  TEXT  NULL
```

Composite `(tenant_id, id)` preferred so FKs cannot cross tenants.

## 7. Module authoring (reuse)

A sold app adds **data**, not controls:

```
modules/<id>/
  forms/
    emp.edit.yaml          # catalog defaults — compiled into FormPort
  frontend/pages/          # <Form id="hr.emp.edit" /> only
  mobile/pages/
  db/                      # FKs per §6
```

```tsx
// module page — no checkbox/radio implementations
<Form formId={route.formId} resourceId={id} />
```

`Form` (kernel) loads the envelope, renders registry controls, applies `req`/`mode`/`rules`, submits to the module URL from the envelope (`post: "/api/v1/opzhub/hr/employees"`) or from route meta. The page does not list fields.

Custom type (`ocr.confidence`) is the **only** reason to touch `registry.register` in a module.

List screens:

```
<Collection formId={route.formId} />
```

Kernel `DataTable` + toolbar: import, export, bulk create / update / delete — only the actions the envelope allows (§8).

## 8. Import, export, bulk create / update / delete

These are **kernel collection features**, not per-application screens. The same toolbar binds every list. Java decides which buttons exist (`io`, `bulk` on the envelope). Letters stay `v c u d` — no extra matrix letters for “import” or “export”.

### 10.1 Envelope (`io` + `bulk`)

List `form_id` (or `coll_id`) returns fields **plus**:

```json
{
  "form": "hr.emp.list",
  "access": { "app": "hr", "f": "emp", "x": "v" },
  "fields": [ /* same FieldDef as §3; columns + import map */ ],
  "io": {
    "export": ["csv", "xlsx", "json"],
    "import": ["csv", "xlsx"],
    "template": true,
    "ops": ["c", "u"]
  },
  "bulk": {
    "c": true,
    "u": true,
    "d": true,
    "max": 500,
    "max_mobile": 50,
    "confirm_d": true
  }
}
```

| Flag | Shown when BE sets it | Letter check (server) |
| ---- | --------------------- | --------------------- |
| `io.export` | Export dialog (formats listed) | `v` + ABAC row filter |
| `io.template` | “Download template” | `v` or `c` (import) |
| `io.import` + `ops` contains `c` | Import → create | `c` |
| `io.import` + `ops` contains `u` | Import → update / upsert | `u` (upsert also needs `c` if new rows) |
| `bulk.c` | Bulk create (grid or paste) | `c` |
| `bulk.u` | Bulk update selected rows | `u` |
| `bulk.d` | Bulk delete selected rows | `d` |

Catalog may **force off** money/post collections (`io.import: []`, `bulk.d: false`) even if the user has `c`/`d`. Envelope is still the runtime truth.

Kernel toolbar: if a flag is missing or false, **do not render** that control. No `if (form.startsWith("hr"))`.

### 10.2 Frontend (common only)

```
common/frontend/src/fields/collection/
├── DataTable.tsx
├── EditableGrid.tsx                 # bulk create / bulk update rows
└── toolbar/
    ├── TableToolbar.tsx             # composes the rest
    ├── ImportButton.tsx
    ├── ImportWizard.tsx             # file → map → dry-run → commit
    ├── ExportButton.tsx
    ├── ExportDialog.tsx             # format + current filters / selection
    ├── BulkActions.tsx              # create / update / delete
    ├── BulkCreateDrawer.tsx
    ├── BulkUpdateDrawer.tsx         # only fields with mode=edit
    └── BulkDeleteDialog.tsx         # ConfirmDialog; lists count, not every row
```

Flutter: same ids under `common/mobile/lib/fields/collection/`.

**Import wizard (kernel):**

1. Optional template download (`GET …/io/{form_id}/template`).
2. File pick — MIME from `io.import` only (CSV / XLSX). Not `image-drop`, not face camera.
3. Column map: file header → field `n`. Defaults when headers match. Unmapped **required** columns block commit.
4. Dry-run (`dry=true`): row errors use the same `rules` / `req` / `fk` as a single form.
5. Commit: `202` + job id when over `bulk.max` or file over YAML size; else sync result. Progress on `/ws/opzhub`.

**Export:** current table filters (and optional selected ids) → format. Browser follows a download URL or job; **do not** assemble the file in SPA memory.

**Bulk create:** `EditableGrid` of empty rows (cap `bulk.max`) or paste TSV. Same FieldDef / lookups as create form.

**Bulk update:** selection → drawer of **changed** fields only (`mode=edit`). Unset fields are not written (patch).

**Bulk delete:** selection → confirm (`confirm_d`) → `op: d` + ids. Server re-checks `d` + ABAC per row; partial fail returns `{ ok, fail: [{ id, err }] }`.

### 10.3 HTTP (Java kernel router → module command)

```
GET  /api/v1/opzhub/io/{form_id}/template?fmt=csv
POST /api/v1/opzhub/io/{form_id}/export     { "fmt", "q", "ids"? }  → stream or 202 job
POST /api/v1/opzhub/io/{form_id}/import?dry=true|&op=c|u|upsert
     multipart file; Idempotency-Key
POST /api/v1/opzhub/io/{form_id}/bulk
     { "op": "c"|"u"|"d", "rows"?, "ids"?, "patch"? }  Idempotency-Key
```

`IoPort` / `BulkPort` in the kernel: resolve `form_id` → module handler. Kernel does not switch on sold app ids. Each row of create/update runs `FormPort.validate`. FK columns resolve via `LookupPort` (id or display unique). Export applies `access.rowFilter`.

Large import/export: `BrokerClient` job (not Python OCR). Stream bytes ([14](14-performance-memory.md)). YAML `security.forms.io_max_rows`, `io_max_upload_mb`.

Ledger / posted documents: catalog `io.money: export_only` (or empty import/bulk). Never silent client override.

### 10.4 Catalog (module)

```yaml
# modules/<id>/forms/emp.list.yaml
id: hr.emp.list
collection: true
io:
  export: [csv, xlsx, json]
  import: [csv, xlsx]
  ops: [c, u]                 # import create / update
  bulk: [c, u, d]
  max_rows: 5000
```

## 9. Flutter

Same envelope JSON, same keys, same `AccessApply`. Lookup uses the same `/lookup/{res}` API. No second rule engine in Dart.

Import uses `file_picker` (CSV/XLSX), **not** the face-login camera path. Export saves/shares the file from the job. Phone bulk `max` is the envelope `bulk.max_mobile` (smaller than web).

## 10. What must not happen

- Kernel or `common/mobile` switching on sold application ids.
- Module-local Checkbox / Radio / Select / TextField copies of the kernel kit.
- Treating module YAML `required:` as the only rule (skip FormEnvelope).
- Putting full field catalogs or ABAC documents on the **login** payload.
- Client-only mandatory checks (no Java `FormPort.validate`).
- Postgres `REFERENCES` to a table that disappears when an optional module is omitted.
- Lookup that queries Postgres from the browser, or a dropdown of all rows without search.
- Face/OCR `image-drop` reused as a generic FK control.
- Module-local import wizards, export buttons, or bulk-delete dialogs (kernel toolbar only).
- Import/export/bulk that ignores envelope `io`/`bulk` (client-invented actions).
- Buffering a whole export in the browser or JVM heap ([14](14-performance-memory.md)).
- Bulk delete without `ConfirmDialog` when `bulk.confirm_d` is true.
- Using import to bypass field `req`/`rules`/FK or ABAC row filters.
