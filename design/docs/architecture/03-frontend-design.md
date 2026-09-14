# 03 — Frontend Design

## 1. Role

The frontend is a **single-page shell** plus **pluggable feature packs**. Every customer gets the same kernel: chrome, session, HTTP/WebSocket clients, i18n, theme, and a complete **field kit** so modules never invent one-off inputs for money, dates, lookups, or grids.

Feature pages live only under `modules/<feature>/frontend/` (web) and `modules/<feature>/mobile/` (Flutter). Removing the module folder removes routes, menus, and lazy chunks on **both** clients.

**Two clients, one plugin model:**

| Client | Kernel folder | v1 stack | Operator / artifact |
| ------ | ------------- | -------- | ------------------- |
| Web SPA | `common/frontend` | React 18+, TypeScript, Vite | `opzgui` |
| Mobile | `common/mobile` | **Flutter** (Dart), Android + iOS | store/IPA/APK per solution |

Both use the same field type keys, `FieldDef` JSON, HTTP/WSS APIs, and module discovery. Feature UI lives under `modules/<feature>/frontend/` (web) and `modules/<feature>/mobile/` (Flutter). Removing the module folder removes **both** clients’ routes for that feature. Web details occupy sections 2–13; Flutter is section 14 onward.

## 2. Kernel responsibilities (common/frontend)

| Area | Responsibility |
| ---- | -------------- |
| Boot | Load public runtime config, session restore, mount shell |
| Module load | Import `generated/module-map.ts`, call each `register(app)` |
| Router | Merge module route tables; unknown paths → 404 |
| Shell | Header, sidebar from registered menus, toasts, command palette slot |
| API | `HttpClient` to `/api/v1/opzhub` and `/api/v1/ai` (same origin via Nginx) |
| Realtime | `WsClient` to **WSS** `/ws/opzhub` (same-origin HTTPS page); subscribe by topic after auth |
| Fields | Registry + renderer + form engine used by all modules. Checkbox, radio, free text, single/multi dropdown live **only** here ([22](22-common-fields-forms-fk.md)) |
| Collection IO | Import, export, bulk create / update / delete — kernel toolbar; flags from BE `io`/`bulk` |
| Access apply | Generic `has(app, feature, letter)` + FormEnvelope `mode`/`req`. **No** sold-app ids in kernel |
| Auth UI | Login/logout **views** may live in `modules/identity`; kernel holds session store. If identity module is absent, shell shows “unauthenticated kernel” or solution-provided `auth.mode` |

Kernel does **not** contain ledger screens, inventory tables, OCR upload wizards, or any domain entity forms. Modules do **not** reimplement primitive controls or RBAC `if (app === "hr")`.

### 2.1 GUI mode — lite vs rich (same code)

The product is **one** SPA and **one** Flutter app. Chrome has two **skins**. Modules do not fork screens.

| Id | Display | Loading |
| -- | ------- | ------- |
| `lite` | Light-weight | Tokens + stroke/SVG icons + one small logo. **No** hero photos, illustration packs, icon fonts, Lottie, or dashboard mosaics. First paint stays small. |
| `rich` | Attractive | Extra **images, icon packs, illustrations**, login backgrounds, richer empty-states. Loaded **lazily** after the shell (never block login or money forms). |

YAML `gui.mode` is the site default. Optional user toggle if `gui.allow_user_choice: true`. Hub live-resolve may set `skin` per company ([20](20-licensing-site-central.md)); omit `skin` to keep the site default.

| Concern | `lite` | `rich` |
| ------- | ------ | ------ |
| Icons | Kernel line set (`currentColor` SVG / Flutter `IconData`) | Pack + optional raster (WebP/SVG sprite); still need a text label |
| Images | Logo only (SVG) | Branding folder `branding/rich/` (heroes, textures, module art) |
| Fonts | System stack or one small webfont | Optional display font; still respect `prefers-reduced-motion` |
| Login | Form-first, no full-bleed photo | Optional background + larger mark; **same** fields and license live-check |
| Menus | Text + 16px icon | Optional colored app tiles; same `menu.ts` ids |
| JS/Dart | Rich chunk **not** in the lite graph | Dynamic `import()` / deferred component; fail → stay lite |
| Network | No image waterfall on boot | Cap concurrent image fetches (`gui.rich.max_inflight`, default 4) |

Kernel API for modules (both clients):

```
ctx.skin.mode                 # "lite" | "rich"
ctx.skin.icon(name)           # always a vector in lite; pack in rich
ctx.skin.image(id)            # null in lite; URL/asset in rich (lazy)
```

Modules **must not** `import './hero.png'` at the top of a page. Put optional art under `modules/<id>/frontend/assets/rich/` (and `mobile/assets/rich/`) and load only via `ctx.skin.image`. Lite ignores that folder so it is not in the lite bundle.

User switch (when allowed): header control; persist `localStorage` / Flutter prefs; no reload of the API session. Switching to lite **unloads** rich images (revoke object URLs).

## 3. Directory structure (kernel fields — complete)

This tree is the reusable catalog. **Every ERP field type is a first-class control or display.** Modules bind to type keys; they do not fork controls.

```
common/frontend/src/fields/
├── registry.ts                      # FieldType → Control + Display + default props
├── Field.tsx                        # <Field name="amount" />
├── FieldLabel.tsx
├── FieldHint.tsx
├── FieldError.tsx
├── Form.tsx                         # schema-driven or declarative children
├── FormContext.tsx
├── useField.ts
├── useForm.ts
├── validation.ts                    # applies FormEnvelope.rules (BE is source of truth)
├── types.ts                         # FieldDef, FieldType, FieldValue, FormEnvelope
├── formatters.ts                    # money, qty, percent, date — locale aware
├── parsers.ts
├── permissions.ts                   # AccessApply — generic app/feature/letter only
├── FormLoader.ts                    # GET /api/v1/opzhub/forms/{form_id}
│
├── layout/
│   ├── FormSection.tsx
│   ├── FormGrid.tsx                 # 12-col responsive
│   ├── FormRow.tsx
│   ├── FormTabset.tsx
│   ├── FormStepper.tsx
│   ├── FormRepeater.tsx             # nested line collections
│   ├── FormAccordion.tsx
│   └── FormActions.tsx              # submit/cancel/secondary
│
├── controls/                        # editable
│   ├── text/
│   │   ├── TextField.tsx
│   │   ├── PasswordField.tsx
│   │   ├── EmailField.tsx
│   │   ├── UrlField.tsx
│   │   ├── PhoneField.tsx
│   │   ├── SearchField.tsx
│   │   └── TextAreaField.tsx
│   ├── numeric/
│   │   ├── NumberField.tsx
│   │   ├── IntegerField.tsx
│   │   ├── DecimalField.tsx
│   │   ├── CurrencyField.tsx        # amount + currency code
│   │   ├── QuantityField.tsx        # amount + UOM
│   │   ├── PercentField.tsx
│   │   ├── RatioField.tsx
│   │   └── FormulaField.tsx         # read-calc with optional override
│   ├── datetime/
│   │   ├── DateField.tsx
│   │   ├── DateTimeField.tsx
│   │   ├── TimeField.tsx
│   │   ├── DateRangeField.tsx
│   │   ├── FiscalPeriodField.tsx
│   │   └── DurationField.tsx
│   ├── choice/
│   │   ├── SelectField.tsx
│   │   ├── MultiSelectField.tsx
│   │   ├── RadioField.tsx
│   │   ├── CheckboxField.tsx
│   │   ├── CheckboxGroupField.tsx
│   │   ├── SwitchField.tsx
│   │   ├── ToggleGroupField.tsx
│   │   └── RatingField.tsx
│   ├── relation/
│   │   ├── LookupField.tsx          # async autocomplete (master data)
│   │   ├── LookupMultiField.tsx
│   │   ├── TreeSelectField.tsx
│   │   └── TagField.tsx
│   ├── structured/
│   │   ├── AddressField.tsx
│   │   ├── GeoPointField.tsx
│   │   ├── JsonField.tsx
│   │   ├── KeyValueField.tsx
│   │   └── MoneySplitField.tsx      # multi-currency split lines
│   ├── media/
│   │   ├── FileField.tsx
│   │   ├── ImageField.tsx
│   │   ├── ImageDropField.tsx       # OCR/scan intake
│   │   └── AttachmentListField.tsx
│   ├── editorial/
│   │   ├── RichTextField.tsx
│   │   ├── MarkdownField.tsx
│   │   └── CodeField.tsx
│   ├── identity/
│   │   ├── ColorField.tsx
│   │   ├── IconField.tsx
│   │   └── MaskedField.tsx          # PAN-like masks; no secrets in logs
│   └── hidden/
│       ├── HiddenField.tsx
│       └── ComputedField.tsx
│
├── display/                         # read-only counterparts (same type keys)
│   ├── TextDisplay.tsx
│   ├── MoneyDisplay.tsx
│   ├── QuantityDisplay.tsx
│   ├── PercentDisplay.tsx
│   ├── DateDisplay.tsx
│   ├── DateTimeDisplay.tsx
│   ├── BooleanDisplay.tsx
│   ├── StatusBadgeDisplay.tsx
│   ├── LinkDisplay.tsx
│   ├── ImageThumbDisplay.tsx
│   ├── FileChipDisplay.tsx
│   ├── AddressDisplay.tsx
│   ├── JsonDisplay.tsx
│   ├── RelativeTimeDisplay.tsx
│   └── AuditStampDisplay.tsx
│
├── collection/                      # tables / ERP lines — reusable everywhere
│   ├── DataTable.tsx                # sort, filter, page, column picker
│   ├── TreeTable.tsx
│   ├── EditableGrid.tsx             # journal lines, inventory lines
│   ├── PivotTable.tsx
│   ├── InfiniteList.tsx
│   ├── KanbanBoard.tsx
│   ├── columns/
│   │   ├── ColumnDef.ts
│   │   └── cellRenderers.ts         # delegates to display/* by field type
│   └── toolbar/                     # import / export / bulk — kernel only (doc 22 §8)
│       ├── TableToolbar.tsx
│       ├── ImportButton.tsx
│       ├── ImportWizard.tsx
│       ├── ExportButton.tsx
│       ├── ExportDialog.tsx
│       ├── BulkActions.tsx          # create, update, delete
│       ├── BulkCreateDrawer.tsx
│       ├── BulkUpdateDrawer.tsx
│       └── BulkDeleteDialog.tsx
│
├── feedback/
│   ├── Spinner.tsx
│   ├── Skeleton.tsx
│   ├── EmptyState.tsx
│   ├── ErrorState.tsx               # msg + hint + copy correlation_id (doc 24)
│   ├── ConfirmDialog.tsx
│   ├── Drawer.tsx
│   └── ToastHost.tsx                # used by shell; field-level inline errors stay in FieldError
│
└── contrib/                         # kernel slots modules fill
    ├── Slot.tsx
    └── slot-names.ts                # "document.actions", "dashboard.widgets", ...
```

Modules may add **custom field types** under `modules/<id>/frontend/fields/` by calling `registry.register('ocr.confidence', ...)`. They must not copy kernel controls.

## 4. Field type catalog (registry keys)

Every key below must exist in `registry.ts` for v1. Display mode uses the same key with `mode: 'view' | 'edit' | 'filter'`.

| Key | Control | Typical ERP use |
| --- | ------- | --------------- |
| `text` | TextField | Names, codes |
| `password` | PasswordField | Identity only |
| `email` | EmailField | Party contacts |
| `url` | UrlField | External refs |
| `phone` | PhoneField | E.164 storage, locale display |
| `search` | SearchField | Toolbars |
| `textarea` | TextAreaField | Memos |
| `richtext` | RichTextField | Policies, notes |
| `markdown` | MarkdownField | Internal docs |
| `code` | CodeField | Formula, mapping scripts |
| `integer` | IntegerField | Counts |
| `decimal` | DecimalField | Rates, factors |
| `number` | NumberField | Generic numeric |
| `money` | CurrencyField | Ledger amounts (ACID values from API, not computed ad hoc in UI) |
| `quantity` | QuantityField | Inventory qty + UOM |
| `percent` | PercentField | Tax, discount |
| `ratio` | RatioField | Fx pairs display |
| `formula` | FormulaField | Read-only calc; server is source of truth |
| `boolean` | SwitchField / CheckboxField | Flags |
| `checkbox-group` | CheckboxGroupField | Multi flags |
| `radio` | RadioField | Exclusive enums |
| `select` | SelectField | Closed enums |
| `multiselect` | MultiSelectField | Tags, types |
| `toggle-group` | ToggleGroupField | View switchers |
| `rating` | RatingField | Optional QA |
| `date` | DateField | Document date |
| `datetime` | DateTimeField | Posted at |
| `time` | TimeField | Shift, cut-off |
| `daterange` | DateRangeField | Reports |
| `fiscal-period` | FiscalPeriodField | Ledger periods |
| `duration` | DurationField | SLA |
| `lookup` | LookupField | Item, account, party |
| `lookup-multi` | LookupMultiField | Allocations |
| `tree-select` | TreeSelectField | CoA, locations |
| `tag` | TagField | Labels |
| `address` | AddressField | Ship-to / bill-to |
| `geopoint` | GeoPointField | Warehouse pin |
| `json` | JsonField | Extensible attributes |
| `keyvalue` | KeyValueField | Metadata |
| `money-split` | MoneySplitField | Multi-currency split |
| `file` | FileField | Attachments |
| `image` | ImageField | Photos |
| `image-drop` | ImageDropField | OCR intake: **image, PDF, Office doc, HTML** (FieldDef `accept`) |
| `attachments` | AttachmentListField | Document sets |
| `color` | ColorField | Admin branding |
| `icon` | IconField | Menu admin |
| `masked` | MaskedField | Partial PAN display |
| `hidden` | HiddenField | IDs |
| `computed` | ComputedField | Client hints only |
| `status` | StatusBadgeDisplay | Doc state |
| `audit` | AuditStampDisplay | Created/updated |

**Money and quantity rules:** UI never posts floating-point JSON. Use string decimals or integer minor units as defined by `common/contracts/fields`. Currency code is ISO 4217. Scale comes from currency master, not hardcoded `2`.

## 5. Field definition contract

**Catalog** YAML may live in the module (`modules/<id>/forms/*.yaml`). **Runtime** FieldDef is the Java **FormEnvelope** ([22](22-common-fields-forms-fk.md)): mandatory / optional / free-text rules / `mode` (edit|view|hide) are computed with RBAC+ABAC and returned on `GET /api/v1/opzhub/forms/{form_id}`. The SPA/Flutter must not treat catalog `required:` as the last word.

```yaml
# Catalog default only — lives in module, not kernel
id: ledger.journal.header
fields:
  - name: doc_date
    type: date
    required: true
  - name: currency
    type: lookup
    lookup: master-data.currency
  - name: lines
    type: repeater
    of: ledger.journal.line
```

Kernel page usage (every sold app):

```
<Form formId={meta.formId} resourceId={id} />
```

TypeScript shape (design) — wire names may be compact (`n`,`t`,`req`,`mode`,`rules`,`opts`,`fk`):

```
FieldDef {
  name: string
  type: FieldTypeKey          # text, textarea, boolean, radio, select, multiselect, lookup, ...
  label?: i18nKey
  required: boolean           # from BE envelope (effective)
  mode: 'edit' | 'view' | 'hide'   # from BE; do not re-derive in the module
  readOnly?: boolean          # alias of mode==view for older binders
  hidden?: boolean            # alias of mode==hide
  placeholder?: i18nKey
  hint?: i18nKey
  defaultValue?: unknown
  options?: Array<{ value, label }>   # radio, select, multiselect, checkbox-group — from BE
  rules?: { min, max, pat, trim, case, charset, fmt, deny, scale, min_sel, max_sel }
  fk?: { res, v, d, multi, q }        # lookup / lookup-multi
  lookup?: { module, resource, displayField, valueField, filters }  # catalog synonym of fk
  repeater?: { of: string, min, max }
  dependsOn?: Array<{ field, op, value, then: Partial<FieldDef> }>  # from BE `when`
  column?: { width, pin, sortable, filterable }   # when used in DataTable
}
```

`FormRepeater` + `EditableGrid` share `FieldDef` so journal lines and inventory lines are the same system.

### 5.1 Primitive reuse (no module copies)

Checkbox, radio, free text (`text`/`textarea`), single dropdown (`select`), multi dropdown (`multiselect`) are implemented **once** in `common/frontend` and `common/mobile`. A module that needs a new *look* uses skin tokens ([§2.1](#21-gui-mode--lite-vs-rich-same-code)), not a new control.

### 5.2 Access apply (no application names in common)

`permissions.ts` / Flutter `access_apply.dart` only:

- `has(app, feature, letter)` against session `a`
- `envelope.fields[].mode` / `req` for forms

Kernel never contains `if (app === "hr")`. Route/menu contributions pass `{ app, feature }` as **data** from `register()`. Detail: [22](22-common-fields-forms-fk.md) §5, [18](18-identity-rbac-abac-oauth2.md).

## 6. Module frontend contract

```
modules/<feature>/frontend/
├── index.ts                 # export function register(ctx: KernelContext)
├── routes.ts                # RouteTable — each route may set formId (opaque)
├── menu.ts                  # MenuContribution[] — { app, feature } data only
├── i18n/en.json
├── api/<feature>-client.ts  # uses kernel HttpClient only
├── pages/                   # <Form formId=... />; no primitive control copies
├── widgets/
└── fields/                  # optional registry.register(...) only for new type keys
```

Catalog form defaults: `modules/<feature>/forms/*.yaml` (consumed by Java `FormPort`, not imported by the kernel SPA).

`register(ctx)` may:

- `ctx.router.addRoutes(...)`
- `ctx.menu.addItems(...)`
- `ctx.slots.contribute('dashboard.widgets', Widget)`
- `ctx.fields.register(...)`
- `ctx.ws.subscribePattern(...)` only for topics this module owns

`register` must be idempotent and must not throw if a required port is missing; it should hide contributions instead.

## 7. Routing and code splitting

- Kernel router is data-driven.
- Each module route uses `React.lazy` (or equivalent) so deleted modules leave **no chunk**.
- Path namespace: `/app/<feature>/...` (example: `/app/ledger/journals`).
- Public auth routes: `/login` from identity. Skin follows `gui.mode` (`lite` form-first vs `rich` images/icons) but **the same** license live-check. On **central** hub, collect `company/user` or `user@company`. **Debounce** `license.live_ms` and `POST …/license/resolve` `{ live: true }` while typing — license server returns logo + `auth[]` (+ optional `skin`). Show password / OAuth / face / fingerprint only from that list. Face uses **live camera** + client PAD (no photo/file). Password focus → `preflight` `method: password`. Full-site companies then authenticate on the mapped `gui` origin ([20](20-licensing-site-central.md)).
- Gateway serves the SPA at `/` and `/app/*`; APIs stay under `/api/v1/*`.

## 8. API and realtime clients (kernel)

```
HttpClient
  erp.get/post/put/patch/delete(path, opts)
  ai.get/post(...)                         # /api/v1/ai
  withIdempotencyKey()                     # required for money POST

WsClient
  connect()
  subscribe(topic, handler)
  unsubscribe(topic)
```

Topics are strings defined in AsyncAPI (`notify.user.{id}`, `ocr.job.{id}`). Frontend never talks to Valkey. Pub/Sub is server-side; the browser only uses **WSS**.

## 9. State policy

| State | Where |
| ----- | ----- |
| Session, locale, theme, **gui.mode** | Kernel store (skin is chrome, not domain) |
| Access matrix | Kernel session: `a.<app>.<feature>` letter string. Hide UI only; server re-checks. |
| Form policy | `FormEnvelope` per screen: `req`, `rules`, `mode`, `io`, `bulk` ([22](22-common-fields-forms-fk.md)) |
| Form draft (unsaved) | Form engine (local) |
| Server entities | Module query cache keyed by resource (React Query or equivalent) |
| Optimistic money posts | Forbidden unless the module documents a compensating API |

## 10. Accessibility and i18n

- Every control has a visible label or `aria-label`.
- Dates/money format via locale from session.
- Kernel dictionary has chrome strings; modules ship their own JSON merged at register time.
- RTL is a kernel theme token concern, not a module fork.

## 11. Build-time module map (design)

`tools/module_gen` writes:

```
// generated/module-map.ts — example shape
export const modules = [
  () => import("@mod/identity/frontend"),
  () => import("@mod/ledger/frontend"),
];
```

If `modules/ocr` is deleted, that import line is absent. Vite/webpack must not scan `modules/*` with a wildcard that includes missing optional packages in a way that fails the build. The generator is the allow-list.

## 12. UX patterns reused by all modules

| Pattern | Kernel primitive |
| ------- | ---------------- |
| Header form + line grid | Form + EditableGrid |
| Master-detail | DataTable + Drawer |
| Lookup create-on-the-fly | LookupField slot `createForm` |
| Long OCR job | ImageDropField (`accept`: image/pdf/doc/html) → job id → WsClient progress → result form |
| Empty/error | EmptyState / ErrorState (`msg` + `hint` + copy `correlation_id`, [24](24-hang-prevention-error-reporting.md)) |
| Destructive post | ConfirmDialog |
| Import / export | `ImportWizard` / `ExportDialog` — formats and ops from envelope `io` ([22](22-common-fields-forms-fk.md) §8) |
| Bulk create / update / delete | `BulkActions` + grid/drawer; `bulk.c`/`u`/`d` from BE |

## 13. Explicit non-goals for the kernel UI

- No chart library locked in kernel (reporting module brings its own, or a thin `ChartFrame` later).
- No CSS framework lock beyond design tokens.
- No second SPA / second Flutter app for “pretty vs light”. Skin is `gui.mode` only.
- No module business validation duplicated from the Java engine; client validation is convenience only (`rules` from FormEnvelope; Java `FormPort.validate` is authoritative).
- No per-application checkbox/radio/select/text implementations in `modules/*/frontend`.
- No per-application import/export/bulk pages; use kernel `Collection` + toolbar.
- No sold-app literals in `common/frontend` access or form code.

## 14. Dual clients (web + Flutter) — same way as web

Mobile is **not** a rewrite of the product. It is the second presentation of the same kernel contracts.

| Concern | Web | Flutter | Shared |
| ------- | --- | ------- | ------ |
| Shell, session, i18n, theme tokens | `common/frontend` | `common/mobile` | token names + `FieldDef` |
| Feature screens | `modules/<id>/frontend` | `modules/<id>/mobile` | same resource URLs |
| Field types | React controls | Flutter widgets | keys in `common/contracts/fields` |
| HTTP / WSS | `HttpClient` / `WsClient` | same ports, Dart clients | `/api/v1/opzhub`, `/api/v1/ai`, `/ws/opzhub` |
| Module map | `generated/module-map.ts` | `generated/module_map.dart` | `opzhubctl module-gen` |
| Register | `register(ctx)` in `index.ts` | `register(ctx)` in `plugin.dart` | idempotent; hide if port missing |
| Config | public JSON (no DB secrets) | same JSON + flavor origin | Nginx or core `/config.json`; includes `gui.mode` |
| Auth | HTTP-only Secure cookie | OS secure storage + Bearer | identity APIs; compact `a` matrix (`vcu`) ([18](18-identity-rbac-abac-oauth2.md)) |
| Face login | `getUserMedia` + WASM PAD | **Camera** + on-device PAD (isolate) | same challenge/proof + `medium`/`high`; **no gallery** |
| Skin | `lite` / `rich` ThemeProvider | same ids, `theme/skins/` | [03](03-frontend-design.md) §2.1 |
| Profile | Vite `dev` vs production build | `--flavor dev` / `prod` | [17](17-dev-prod-implementation.md) |

A module may ship **web only** (omit `mobile/`) or **mobile only** (omit `frontend/`). `module.yaml` sets `runtimes.frontend` and `runtimes.mobile` independently. Admin-heavy screens that are desktop-only omit Flutter; camera/OCR intake that is phone-first still **registers web** if the catalog page exists.

**No second API.** Flutter must not talk to Postgres, Valkey, or internal ports 8114/8117. Only the public origin (prod: `https://customer-host`; dev: flavor `OPZHUB_ORIGIN`).

## 15. Flutter kernel (`common/mobile`)

```
common/mobile/                         # KERNEL — Flutter package
├── pubspec.yaml                       # name: opzhub_mobile
├── analysis_options.yaml
├── lib/
│   ├── main.dart                      # flavor boot → config → register modules → AppShell
│   ├── app/
│   │   ├── app_shell.dart
│   │   ├── router.dart                # go_router (or equivalent); merges module routes
│   │   └── guards.dart
│   ├── kernel/
│   │   ├── config.dart                # public JSON + dart-define origin
│   │   ├── module_loader.dart
│   │   ├── ports.dart                 # same port names as web
│   │   └── types.dart
│   ├── api/
│   │   ├── http_client.dart           # /api/v1/opzhub and /api/v1/ai
│   │   ├── ws_client.dart             # WSS /ws/opzhub
│   │   └── errors.dart
│   ├── auth/
│   │   ├── session.dart               # flutter_secure_storage
│   │   ├── session_store.dart
│   │   └── face_pad.dart              # live Camera + PAD isolate (not gallery)
│   ├── i18n/
│   ├── theme/
│   │   ├── tokens.dart                # same token names as web theme
│   │   └── skins/                     # lite + rich; rich assets deferred
│   ├── generated/                     # BUILD ARTIFACT
│   │   └── module_map.dart
│   └── fields/                        # same type keys as web §3–4
│       ├── registry.dart
│       ├── field.dart
│       ├── form.dart
│       ├── types.dart                 # FieldDef parsed from shared JSON
│       ├── controls/
│       ├── display/
│       ├── collection/
│       └── feedback/
├── android/
├── ios/
└── flavors/                           # dev | staging | prod
    ├── dev.dart
    └── prod.dart
```

Kernel responsibilities match the web table in §2: boot, module load, router, shell, API, realtime, fields, session. Kernel does **not** contain ledger/inventory/OCR domain screens.

`tools/module_gen` also writes:

```
// generated/module_map.dart — example shape
final modules = <OpzModule Function()>[
  identity.register,
  ledger.register,
];
```

If `modules/ocr` is deleted, that entry is absent. Flutter must not `import '../../modules/ocr/...'` from the kernel.

## 16. Module mobile contract

```
modules/<feature>/mobile/
├── plugin.dart              # void register(KernelContext ctx)
├── routes.dart
├── menu.dart                # bottom-nav / drawer contributions
├── l10n/
├── api/<feature>_client.dart
├── pages/
├── widgets/
└── fields/                  # optional registry.register('ocr.confidence', ...)
```

`register` may add routes, menu items, slots, field types, and WS subscriptions — same rules as web §6 (idempotent, no throw if a port is missing).

Path namespace stays `/app/<feature>/...` so deep links match the SPA (app links / universal links on the public host).

`module.yaml`:

```yaml
runtimes:
  frontend: true    # web
  mobile: true      # Flutter
  backend: true
```

## 17. Field kit on Flutter (same keys)

Every registry key in §4 has a Flutter widget. Modules bind to **keys**, not to React components. Shared source of truth is `common/contracts/fields/field-schema.json`. **Runtime** `req` / `mode` / `rules` / `opts` / `fk` come from the same FormEnvelope as web ([22](22-common-fields-forms-fk.md)). Module YAML is catalog default only.

| Key | Flutter (illustrative) |
| --- | ---------------------- |
| `text` / `textarea` / `email` / `phone` | `TextFormField` variants; `rules` from FormEnvelope |
| `boolean` / `radio` / `select` / `multiselect` / `checkbox-group` | same keys as web; `opts` from BE |
| `money` / `quantity` / `percent` | decimal formatters; same scale rules as web (no float JSON) |
| `date` / `datetime` / `fiscal-period` | Material/Cupertino pickers |
| `lookup` / `lookup-multi` | async autocomplete; `fk.res` from envelope, same `/lookup/{res}` |
| `image-drop` | gallery, camera, **or file** (PDF / doc / HTML); same OCR job API — **not** face login |
| `file` / `attachments` | `file_picker` + documents API |
| collection `DataTable` / `EditableGrid` | horizontal-scroll tables or line editors; phone uses stacked line cards + optional landscape table |

Custom types: `modules/<id>/mobile/fields` calls `registry.register` with the **same key** the web module registered, so a shared `FieldDef` renders on both clients.

## 18. Auth, config, and TLS on the device

- Login uses `identity` APIs (password, **OAuth2 + PKCE**, **face**, **fingerprint**). Hub login **live-resolves** the company against the license server while typing; password is shown only if `auth` includes `password`. Flutter stores the access token in **secure storage** and sends `Authorization: Bearer`.
- **Face on Android and iOS is the same contract as web** ([18](18-identity-rbac-abac-oauth2.md) §2.3): live front camera, client PAD, `medium`/`high` from public config, then `POST …/identity/login/face`. There is no mobile-only match API.
- Fingerprint: `local_auth` / WebAuthn for `platform`; kiosk scanner for `template`.
- Public config JSON is identical (`solutionId`, `modules`, `wsPath`, `apiOpzhub`, `apiAi`, `gui`, `auth.bio`). Flutter adds `origin` from `--dart-define=OPZHUB_ORIGIN` (dev) or the prod flavor (https origin only). `gui.mode` is `lite` or `rich` (same as web).
- Prod Flutter builds **pin** the public CA / SPKI ([12](12-transport-security-tls.md)). Dev flavor may trust a local mkcert.
- Push notifications (optional, later) subscribe through the same `notifications` module topics; they do not bypass the broker.

### 18.1 Face login on Flutter (Android + iOS)

Identity UI: `modules/identity/mobile/` (login camera page). Kernel PAD: `common/mobile/lib/auth/face_pad.dart`. Same `challenge` / `proof` / `frames[]` as the SPA.

| Rule | Flutter |
| ---- | ------- |
| Camera | `camera` plugin, **front** lens only. Preview is a live `CameraController` stream. |
| Forbidden | `image_picker`, gallery, files, screenshots, sharing a video into the app for login or enroll. |
| PAD | On-device Face Landmarker (**MediaPipe** / pinned TFLite, Apache-2.0). Run on an **Isolate** (or FFI); do not block the UI isolate. |
| Photo / replay | Landmark motion + flow over `min_frames`; reject freeze-frames, printed photos, and another device playing a video (bezel / moiré / flat depth when the camera provides it). |
| Challenge | Perform server `acts` (blink / turn / nod) on the live stream; timestamps in `proof`. |
| Profile | Use resolved `high`/`medium` from challenge (server `python.vision.quality` auto). Do not silently drop on a slow phone (password/OAuth instead). |
| Permissions | Android `CAMERA`; iOS `NSCameraUsageDescription`. Denied → generic fail, no gallery fallback. |
| Upload | After PAD pass: JPEG frames + `proof` to **Java** `POST /api/v1/opzhub/identity/login/face`. Never call `opzhub-be-core` from the phone. |
| Matcher | Still Python via Java. The app does **not** store templates or decide “this is Ada”. |

`gui.mode: lite` does not skip PAD. OCR `image-drop` may still open the gallery; **face login must not reuse that widget**.

## 19. UX patterns (mobile)

Reuse the same patterns as §12, adapted to phone chrome:

| Pattern | Flutter primitive |
| ------- | ----------------- |
| Header form + lines | Form + line list / EditableGrid |
| Master-detail | list page → detail route (not only a drawer) |
| OCR intake | camera, gallery, or file (PDF/doc/HTML) → same job id → WS progress |
| Destructive post | `ConfirmDialog` equivalent |
| Import / export / bulk | Same envelope `io`/`bulk`; `file_picker` for CSV/XLSX; smaller `bulk.max_mobile` |

Kernel still has no chart library lock. Reporting brings its own on both clients if needed.

## 20. Explicit non-goals (mobile)

- No React Native / Kotlin-only / Swift-only second app. **Flutter is the v1 mobile stack.**
- No business posting in Dart; Java remains the ACID owner.
- No embedding of OpenCV/Paddle in the app; OCR and face match stay on `opzpy` / `opzhub-be-core`. Flutter runs **live PAD + capture** only ([18](18-identity-rbac-abac-oauth2.md) §2.3).
- No Kotlin/Swift face SDK as a second login stack. PAD lives in `face_pad.dart` so Android and iOS stay one codebase.
- No separate “mobile backend.”
