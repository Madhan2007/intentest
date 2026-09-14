# 02 — Repository Folder Structure

This is the canonical tree. Folders marked **KERNEL** are never removed. Folders marked **MODULE** are independently deletable. Folders marked **SOLUTION** are per-customer.

Implementation must not invent a parallel **product** tree. If a file type is not listed, it belongs either in `common/` (shared) or inside a module (feature).

**QA is not this tree.** Unit, functional, and performance suites live in a **separate testing application** (recommended second git repo). Layout and gates: [21 — Testing application](21-testing-application.md). Do not add `testing/` under this product repo in this design drop.

## 1. Top level

```
/
├── README.md                          # points to docs/ (implementation later)
├── docs/                              # this design set
│   ├── README.md
│   └── architecture/
├── common/                            # KERNEL
│   ├── frontend/                      # web SPA (React)
│   ├── mobile/                        # Flutter Android/iOS
│   ├── backend/                       # Java Spring Boot kernel
│   ├── python/                        # FastAPI + worker kernel
│   ├── scripts/                       # CLI kernel
│   └── contracts/                     # shared OpenAPI/AsyncAPI/field schemas
├── modules/                           # VENDOR catalog (50+ apps). Customer pack = subset only.
│   ├── identity/                      # shared
│   ├── admin/                         # shared
│   ├── hr/                            # sold application
│   ├── ticketing/                     # sold application
│   ├── ledger/
│   ├── inventory/
│   ├── master-data/
│   ├── notifications/                 # in-app / WS (not SMTP)
│   ├── mail/                          # shared send+receive; Python protocol (doc 23)
│   ├── documents/
│   ├── ocr/
│   ├── image-processing/
│   ├── reporting/
│   └── …                              # more applications; never copy all into a delivery
├── solutions/                         # SOLUTION packages
│   ├── _template/
│   └── acme-example/
├── gateway/                           # Nginx templates (design in doc 09)
├── infra/                             # compose templates, EC2 bootstrap, **non-root wrappers**
│   └── wrappers/
│       ├── lib/
│       │   ├── opzhub-common.sh
│       │   └── opzhub-ctl.sh            # start/stop/status/release
│       ├── init-system.sh
│       ├── init-home.sh
│       ├── run-opzhub-engine.sh
│       ├── run-opzhub-be-core.sh
│       ├── run-web.sh
│       ├── run-nginx.sh
│       ├── run-job.sh
│       ├── run-script.sh
│       ├── run-cron.sh
│       ├── run-opzhubctl.sh             # opzhubctl
│       └── system/                      # copied once to /usr/local/bin
│           ├── opzhubctl
│           ├── opzhub-run-cron
│           └── opzhub-run-job
├── platform/                          # compose overlay, env templates
│   ├── RELEASE
│   ├── catalog/
│   │   └── applications.yaml          # index of 50+ sold apps (not a default install)
│   └── config/
│       ├── platform.yaml              # db.type, cache.type, broker.type
│       └── logging.yaml
└── tools/                             # generators: module-map, nginx locations (design)
```

## 2. Common — frontend kernel

Reusable for **all** solutions. Contains the application shell and the **field kit** (every input/display type ERP screens need). Feature pages do not live here.

```
common/frontend/
├── package.json
├── tsconfig.json
├── vite.config.ts                     # aliases: @kernel, @modules (generated map)
├── index.html
├── public/
└── src/
    ├── main.ts                        # boot: load config → discover modules → mount
    ├── app/
    │   ├── AppShell.tsx               # nav, header, session, toasts
    │   ├── router.ts                  # aggregates module route tables
    │   ├── guards.ts
    │   └── ErrorBoundary.tsx
    ├── kernel/
    │   ├── config.ts                  # reads runtime config (db/cache not used in browser)
    │   ├── module-loader.ts           # imports generated/module-map
    │   ├── ports.ts                   # NotifyPort, DocumentsPort, etc. (optional facades)
    │   └── types.ts
    ├── api/
    │   ├── http-client.ts             # ERP + AI base URLs via gateway
    │   ├── ws-client.ts               # WebSocket + reconnect
    │   └── errors.ts
    ├── auth/
    │   ├── session.ts
    │   └── SessionStore.ts
    ├── i18n/
    │   ├── index.ts
    │   └── dictionaries/en.json       # kernel strings only
    ├── theme/
    │   ├── tokens.ts
    │   ├── skins/
    │   │   ├── lite/                # line icons; no raster pack
    │   │   └── rich/                # deferred; not in lite graph
    │   └── ThemeProvider.tsx
    ├── state/
    │   └── store.ts                   # session + UI chrome; not domain entities
    ├── generated/                     # BUILD ARTIFACT (do not hand-edit)
    │   └── module-map.ts
    └── fields/                        # ★ reusable for all modules — docs 03 + 22 (no app names)
        ├── registry.ts
        ├── Field.tsx                  # generic renderer by type key
        ├── Form.tsx
        ├── FormContext.tsx
        ├── validation.ts
        ├── layout/
        ├── display/
        └── controls/
```

`@modules` is a virtual alias resolved only through `generated/module-map.ts`.

## 3. Common — mobile kernel (Flutter)

Same plugin-play as web. Feature screens are **not** here. Spec: [03](03-frontend-design.md) §14–20.

```
common/mobile/
├── pubspec.yaml                       # opzhub_mobile
├── lib/
│   ├── main.dart
│   ├── app/
│   ├── kernel/
│   ├── api/                           # HTTPS + WSS to public origin
│   ├── auth/                          # Bearer, face_pad (live Camera PAD)
│   ├── i18n/
│   ├── theme/
│   │   ├── tokens.dart                # same token names as web
│   │   └── skins/                     # lite + deferred rich
│   ├── generated/module_map.dart      # BUILD ARTIFACT
│   └── fields/                        # same FieldType keys as web
├── android/
├── ios/
└── flavors/                           # dev | staging | prod
```

## 4. Common — Java backend kernel

```
common/backend/
├── pom.xml                            # parent BOM; modules are optional jars or source roots
├── src/main/java/com/erp/kernel/
│   ├── KernelApplication.java         # @SpringBootApplication in kernel package
│   ├── config/
│   │   ├── PlatformProperties.java    # binds platform.yaml
│   │   ├── DataClientConfig.java      # selects db.type
│   │   ├── CacheClientConfig.java     # selects cache.type
│   │   ├── BrokerClientConfig.java
│   │   ├── WebSocketConfig.java
│   │   └── VirtualThreadConfig.java
│   ├── web/
│   │   ├── KernelExceptionHandler.java
│   │   ├── CorrelationFilter.java
│   │   └── HealthController.java
│   ├── security/
│   ├── tenancy/
│   ├── audit/
│   ├── module/
│   │   ├── ModuleCatalog.java
│   │   └── ModuleNotPresentException.java
│   ├── data/
│   │   ├── client/                    # DataClient API (doc 07)
│   │   └── server/                    # PostgresDataServer, … implementations
│   ├── cache/
│   │   ├── client/
│   │   └── server/
│   ├── broker/
│   ├── realtime/
│   │   ├── WsGateway.java
│   │   └── CachePubSubBridge.java
│   └── generated/                     # BUILD ARTIFACT
│       └── ModuleAutoConfigurations.java
└── src/main/resources/
    ├── application.yml                # spring entry; includes platform.yaml
    └── META-INF/
```

Feature controllers **do not** live under `com.managemyopz.kernel`.

## 5. Common — Python kernel

```
common/python/
├── pyproject.toml
├── opzhub_kernel/
│   ├── __init__.py
│   ├── app_factory.py               # FastAPI factory, CORS, health
│   ├── settings.py                  # YAML: db.type, cache.type, broker.type
│   ├── discover.py                  # module plugin loader
│   ├── data/
│   │   ├── client.py
│   │   └── servers/
│   │       ├── postgres.py
│   │       └── memory.py            # test
│   ├── cache/
│   │   ├── client.py
│   │   └── servers/
│   │       ├── valkey.py
│   │       └── memory.py
│   ├── broker/
│   ├── workers/
│   │   └── base.py                  # async worker bootstrap
│   ├── mail/                        # SMTP/IMAP/MIME adapters — doc 23
│   │   ├── client.py
│   │   ├── send/
│   │   ├── receive/
│   │   └── mime/
│   └── generated/
│       └── module_map.py
└── tests/                           # kernel tests only
```

Service entrypoints (implementation later): `services/ai/main.py` would call `create_app()`; workers call `run_worker()`.

## 6. Common — scripts kernel

```
common/scripts/
├── opzhubctl                     # entry (python -m or bash trampoline)
├── pyproject.toml                # if Python CLI
├── erp_scripts/
│   ├── cli.py                    # root parser
│   ├── config.py                 # same YAML as platform
│   ├── logging.py
│   ├── data_client.py            # wraps kernel DataClient
│   ├── cache_client.py
│   ├── compose.py                # up/down helpers
│   ├── module_gen.py             # regenerate maps
│   └── commands/                 # kernel commands only
│       ├── health.py
│       ├── migrate.py            # dispatches per present module
│       └── doctor.py
└── templates/                    # new-module cookiecutter (design)
```

## 7. Common — contracts

```
common/contracts/
├── fields/
│   └── field-schema.json          # JSON Schema for field definitions
├── openapi/
│   └── kernel.yaml                # health, errors, auth token shape
├── asyncapi/
│   └── realtime.yaml              # notification and job progress envelopes
└── platform/
    └── platform.schema.yaml       # validates platform.yaml
```

## 8. Module inner structure (template)

Every feature uses this shape. Omit runtime trees that `module.yaml` sets to false.

```
modules/<feature>/
├── module.yaml
├── README.md                      # module-level design notes (optional)
├── contracts/
│   ├── openapi.yaml
│   └── events.yaml
├── forms/                         # catalog defaults; runtime = Java FormEnvelope (doc 22)
│   └── <screen>.yaml
├── db/
│   ├── 0001_<feature>_init.sql    # applied via DataClient migration runner
│   └── rollback/
├── frontend/
│   ├── index.ts                   # export function register(app)
│   ├── routes.ts
│   ├── menu.ts
│   ├── i18n/en.json
│   ├── api/
│   ├── pages/
│   ├── widgets/                   # dashboard contributions
│   ├── fields/                    # module-specific field extensions only
│   └── assets/
│       └── rich/                  # optional art; never static-imported
├── mobile/                        # Flutter; omit if runtimes.mobile=false
│   ├── plugin.dart                # register(ctx)
│   ├── routes.dart
│   ├── menu.dart
│   ├── pages/
│   ├── fields/
│   └── assets/
│       └── rich/
├── backend/                       # Java
│   └── src/main/java/com/erp/modules/<feature>/
│       ├── <Feature>AutoConfiguration.java
│       ├── api/
│       ├── application/
│       ├── domain/
│       └── adapter/
├── python/
│   ├── plugin.py                  # register(app, worker_registry)
│   ├── routers/
│   ├── workers/
│   └── pipelines/                 # opencv/ocr/bio — never import from kernel
└── scripts/
    ├── cli.yaml
    └── commands/
```

## 9. Solutions

```
solutions/_template/
├── solution.manifest.yaml
├── branding/
│   ├── logo.svg
│   ├── theme.override.json
│   └── rich/                        # heroes, icon pack; unused in lite
├── config/
│   ├── platform.override.yaml     # db.type, cache.type, urls
│   └── secrets.env.example
└── README.md

solutions/acme-example/
├── solution.manifest.yaml         # enabled module list
├── branding/
├── config/
└── PACKAGING.md                   # which folders to copy from modules/
```

## 10. Gateway, infra, platform (design placeholders)

These directories are reserved. **Do not add live compose/nginx files in this design drop.** Specs live in [09 — Docker, Nginx & EC2](09-docker-nginx-ec2.md).

```
gateway/
  nginx.conf.template              # specified in doc 09, not implemented
infra/
  docker-compose.yml.template          # prod (doc 09)
  docker-compose.dev.yml.template      # overlay (doc 17)
  volumes.md
  ec2-bootstrap.sh.template
platform/config/
  platform.yaml                    # schema specified in doc 08
platform/RELEASE                   # version stamped into the upgrade tarball
platform/catalog/applications.yaml # 50+ app index; packaging reads this, does not copy all folders
```

## 11. Path hygiene rules

1. Imports flow **inward**: module → kernel. Never kernel → specific module.
2. Module → module is **forbidden**. Use kernel ports.
3. Scripts call kernel clients, not `psql` or `valkey-cli` except in break-glass `opzhubctl doctor --raw` (documented exception).
4. SQL lives in `modules/<feature>/db/` or kernel `common/backend` only for kernel tables (`schema_migrations`, `audit_outbox` if generic).
5. Generated maps are the only files allowed to mention module package names in the kernel tree (web, Flutter, Java, Python).
7. Customer **delivery** trees must not contain `modules/<id>` unless it is in `enabled` ∪ `requires`. Vendor source may hold 50+ folders.
8. Do not put the QA application inside `common/` or `modules/`. That tree is specified only in [21](21-testing-application.md) and is not shipped in `/home/tsuser/opzhub`.
9. Primitive inputs (checkbox, radio, free text, single/multi dropdown), import/export/bulk CUD, and RBAC apply live only in `common/frontend` and `common/mobile`. Modules register `{ app, feature, formId }` as data. Foreign keys and collection IO: [22](22-common-fields-forms-fk.md).
10. SMTP/IMAP/MIME live in `common/python/opzhub_kernel/mail` + `modules/mail`. Sold apps add `mail-templates/` only and call Java `MailPort` ([23](23-mail-send-receive.md)).

## 12. Mapping features to folders (v1 catalog)

Vendor repo: many folders. **Customer pack: only bought applications + shared `requires`.** See [10](10-solution-composition.md).

| Application | Path | Example customer |
| ----------- | ---- | ---------------- |
| Identity | `modules/identity` | Shared with UI packs |
| Admin | `modules/admin` | Shared with UI packs |
| Mail | `modules/mail` | Shared when a sold app `requires` email ([23](23-mail-send-receive.md)) |
| HR | `modules/hr` | Customer 1, customer 3 |
| Ticketing | `modules/ticketing` | Customer 2, customer 3 |
| Ledger | `modules/ledger` | Finance subset |
| Inventory | `modules/inventory` | Stock subset |
| … | `modules/<id>/` | Next sold app = new folder |

A kernel-only appliance is valid (ops console, health, empty menu). An HR-only appliance must not contain `modules/ticketing`.
