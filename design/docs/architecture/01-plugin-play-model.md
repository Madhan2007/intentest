# 01 — Plug-and-Play Model

## 1. Intent

ManageMyOpz is a **plug-and-play application platform**. The vendor catalog holds **50+ applications** (HR, ticketing, ledger, inventory, OCR, …). A customer does **not** receive that catalog.

A customer solution is:

- the immutable kernel (`common/`)
- a small set of **shared** modules they need (usually `identity`, `admin`)
- **only** the applications they bought

Implementation teams must be able to:

1. **Include** an application by copying `modules/<app>/` into the customer pack.
2. **Exclude** an application by **never copying** that folder (not by hiding a menu flag).
3. **Disable** an application temporarily via YAML if the folder is already on disk (ops overlay).
4. Boot with **zero code changes** in `common/` after a folder is omitted.

The unit of plug is a **module folder** (one application). It is not a feature flag inside a monolith screen.

### 1.1 Delivery examples (same kernel, different packs)

| Customer | They asked for | Folders on the appliance |
| -------- | -------------- | ------------------------ |
| 1 | HR only | `common/` + `identity` + `admin` + **`hr`** |
| 2 | Ticketing only | `common/` + `identity` + `admin` + **`ticketing`** |
| 3 | HR and ticketing | `common/` + `identity` + `admin` + **`hr`** + **`ticketing`** |

Customer 1’s tarball **must not contain** `modules/ticketing`, `modules/ledger`, or the other 47+ application folders. Customer 2 must not contain `hr`. Customer 3 still does not receive ledger, OCR, inventory, etc.

`opzhubctl package` copies **enabled ∪ requires-closure** only. A wildcard `COPY modules/` from the vendor monorepo is forbidden.

### 1.2 What “common modules” means

| Kind | What | Shipped |
| ---- | ---- | ------- |
| Kernel | `common/` (shell, field kit, DataClient, CLI, Compose/Nginx) | **Every** customer |
| Shared applications | `identity`, `admin` (and others only if `requires` says so, e.g. `notifications`, `mail`) | When the solution needs login/settings |
| Sold applications | `hr`, `ticketing`, `ledger`, … (50+ in the catalog) | **Only if listed** in `solution.manifest.yaml` |

Shared applications are still folders under `modules/`. They are not baked into `common/`. A kernel-only smoke appliance can ship with **zero** `modules/*`.

## 2. Three layers of composition

```
┌──────────────────────────────────────────────────────────┐
│  SOLUTION (customer package)                             │
│  solutions/<customer>/solution.manifest.yaml             │
│  + env, branding, enabled module list                    │
└────────────────────────────┬─────────────────────────────┘
                             │ selects
┌────────────────────────────▼─────────────────────────────┐
│  MODULES (optional, removable)                           │
│  modules/<feature>/{frontend,mobile,backend,python,scripts,db} │
└────────────────────────────┬─────────────────────────────┘
                             │ depends on
┌────────────────────────────▼─────────────────────────────┐
│  COMMON KERNEL (never removed)                           │
│  common/{frontend,mobile,backend,python,scripts,contracts} │
└──────────────────────────────────────────────────────────┘
```

| Layer | Removable? | Contains |
| ----- | ---------- | -------- |
| `common/` | No | Shell (web + Flutter), field kit, clients, security, app factories, CLI |
| `modules/<id>/` | Yes | **One application** (HR, ticketing, …) or one shared app (identity) |
| `solutions/<id>/` | Per customer | Manifest (the pick list), theme, secrets refs |

## 3. Discovery rules (fail closed)

All runtimes use the **same discovery algorithm**:

1. Scan `modules/*` for directories that contain `module.yaml`.
2. Intersect with `solutions/<id>/solution.manifest.yaml` → `modules.enabled`.
3. If a folder exists but is not in `enabled`, skip registration (treat as disabled).
4. If `enabled` lists a module whose folder is missing, **log error and skip** — do not crash the kernel. Mark solution health as `degraded`.
5. If a remaining module declares `requires: [other]` and `other` is absent, skip the dependent module and emit a composition error.

Physical deletion is always sufficient: no leftover imports in `common/` may reference a module by hardcoded path.

## 4. Module contract (`module.yaml`)

Every module folder **must** contain this file at its root. Absence means the directory is ignored.

```yaml
id: ocr                          # kebab-case, unique
version: 1.0.0
title: Document OCR
runtimes:                        # which trees this module contributes
  frontend: true                 # web (React)
  mobile: true                   # Flutter; omit folder if false
  backend: true                  # Java
  python: true
  scripts: true
requires:
  - documents
  - notifications
optional:
  - inventory                    # enriches extraction → stock docs if present
provides:
  routes:
    - /documents/ocr
  api_prefixes:
    - /api/v1/opzhub/ocr
    - /api/v1/ai/ocr
  workers:
    - ocr.ingest                 # mime → pages (pdf/doc/html/image)
    - ocr.extract
    - ocr.parse
  events:
    - ocr.job.progress
    - ocr.job.completed
  menus:
    - id: documents.ocr
      parent: documents
migrations:
  backend: true
  python: false
access:                            # short feature ids for login matrix (doc 18)
  features:
    - { id: job, title: OCR jobs }
    - { id: mdl, title: Models }
```

### 4.1 Required inner layout (only existing runtimes)

A module includes **only the runtimes it needs**. Empty runtime folders are forbidden; omit them.

```
modules/ocr/
  module.yaml
  frontend/          # if runtimes.frontend (web)
  mobile/            # if runtimes.mobile (Flutter)
  backend/           # if runtimes.backend (Java)
  python/            # if runtimes.python
  scripts/           # if runtimes.scripts
  db/                # SQL or data-client migration packs
  contracts/         # optional OpenAPI fragments, asyncapi events
```

Exact inner trees are defined in documents 03–06. Optional `frontend/assets/rich/` and `mobile/assets/rich/` are **not** loaded when `gui.mode: lite`.

## 5. Registration SPI (per runtime)

Modules **self-register**. The kernel never lists feature class names.

| Runtime | Mechanism |
| ------- | --------- |
| Frontend (web) | Each module exports `register(app: KernelApp)` from `frontend/index.ts`. Kernel imports from the generated map. |
| Mobile (Flutter) | Each module exports `register(ctx)` from `mobile/plugin.dart`. Kernel imports from `generated/module_map.dart`. Same discovery as web. |
| Java | Spring `AutoConfiguration.imports` **generated at build** from present modules, or `META-INF/erp/module.imports`. Feature `@Configuration` classes live only inside the module package. |
| Python | `opzhub_kernel.discover_modules()` imports `modules.<id>.python.plugin:register(app, workers)`. |
| Scripts | CLI scans `modules/*/scripts/cli.yaml` and attaches subcommands. |

**Build-time map:** a generator (design-only here) reads the manifest + disk and writes:

- `common/frontend/src/generated/module-map.ts`
- `common/mobile/lib/generated/module_map.dart`
- `common/backend/src/generated/ModuleImports.java` (or `spring.factories`)
- `common/python/opzhub_kernel/generated/module_map.py`

Generated files are artifacts, not hand-edited. They must be regenerable after a folder is deleted.

## 6. Packaging procedure (deliver only what was bought)

Vendor **source** repo may contain 50+ application folders. A **customer pack** is built, not copied wholesale.

```
opzhubctl package --solution solutions/acme-hr/ --out dist/acme-hr/
```

1. Copy `common/`, `gateway/`, `infra/`, `platform/`, `tools/` (kernel).
2. Resolve `modules.enabled` plus each module’s `requires` (hard dependencies).
3. Copy **only** those `modules/<id>/` directories.
4. Write that customer’s `solutions/<id>/` (manifest, branding, config).
5. **Assert** no other `modules/*` exist in the output (fail the build if they do).
6. `opzhubctl module-gen` on the **pack**, not on the full catalog.
7. Build images and Flutter binaries from the pack context.

To add ticketing later for customer 1: copy `modules/ticketing` into a new pack, add it to `enabled`, regenerate, rebuild. Do not ship unused apps “just in case.”

YAML `disabled:` is for staged rollout **on disk**. Production packs that must not contain unused IP **omit the folder**.

## 7. Disable without delete (ops overlay)

```yaml
# solutions/acme-hr/solution.manifest.yaml  — HR only; ticketing folder absent
modules:
  enabled:
    - identity
    - admin
    - hr
  disabled: []
```

Use this for staged rollouts. Production customer appliances that must not contain code **delete the folder**.

## 8. Dependency and conflict rules

- `requires` is hard. Missing required module → dependent does not load.
- `conflicts` (optional list) — two modules cannot be enabled together (example: `ocr-paddle` vs `ocr-tesseract` if split later).
- Kernel provides **extension slots** (menu, dashboard widgets, document actions). Modules contribute contributions; kernel renders whatever was registered.
- Cross-module API calls go through **kernel ports** (`DocumentsPort`, `NotifyPort`), never through a sibling module’s internal package. If the port implementation is missing, the caller receives a typed `ModuleNotPresent` and hides the action in UI.

## 9. Versioning and compatibility

- Kernel declares `kernel.api_version` (e.g. `2026.1`).
- Module declares `compatible_kernel: ">=2026.1 <2027.0"`.
- CI rejects a module that imports another module’s internal path (`modules.inventory.backend.internal`).

## 10. Testing the plugin model

Design-level acceptance tests (to be automated at implementation time):

| Test | Pass criteria |
| ---- | ------------- |
| Kernel-only boot | Pack with **zero** applications; health green; empty shell. |
| HR-only pack | `enabled: [identity, admin, hr]`; no `ticketing` folder; no ticketing routes/tables. |
| Ticketing-only pack | `enabled: [identity, admin, ticketing]`; no `hr` folder. |
| HR + ticketing pack | both folders present; both menus; still **no** ledger/OCR unless listed. |
| Package guard | `opzhubctl package` fails if output contains a module not in enabled∪requires. |
| Remove ocr | Delete folder; regenerate; no OCR symbols in bundle; no OCR queues. |
| Broken requires | Enable `ocr` without `documents` → ocr skipped, solution `degraded`, kernel up. |
| DB/cache swap flags | Same applications; change YAML types; clients bind to new servers (see doc 07). |

## 11. What must never happen

- `common/frontend` importing `@modules/ocr/...` in a static import (same for `common/mobile` → a module path).
- A shared layout file with `if (feature === 'ocr')` hardcoding.
- SQL migrations for OCR inside the kernel migration tree.
- Docker / package context that `COPY`s the whole vendor `modules/` tree (50+ apps) into a customer image.
- Delivering unused applications “for future enablement” on a production appliance.
