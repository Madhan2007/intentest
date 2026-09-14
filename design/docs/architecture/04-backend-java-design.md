# 04 — Backend Java Design (ManageMyOpz core engine)

## 1. Role

Spring Boot is the **system of record** for ERP writes: multi-currency ledgers, inventory, master data, identity, and any transaction that must be ACID. It is also the **WebSocket gateway** for browsers and the **bridge** from Valkey Pub/Sub to connected clients.

It is **not** the place to run OpenCV, PaddleOCR, or long image pipelines. Those are enqueued to the broker and executed by Python workers.

## 2. Process model

- Java 21+ with **virtual threads** enabled for request handling (`spring.threads.virtual.enabled` or equivalent structured concurrency).
- CPU-bound image work is still forbidden on virtual threads inside this process; offload.
- Production heap, pool, and leak rules: [14 — Performance & Memory](14-performance-memory.md).
- One Docker service: `opzhub-be-app`. Horizontal scale: N replicas sharing Valkey Pub/Sub and PostgreSQL.

## 3. Package layout (kernel)

```
com.managemyopz.kernel
  KernelApplication
  config          PlatformProperties, client factories
  web             filters, exception model, health
  security        session, OAuth2 callback, AccessPort (RBAC+ABAC)
  forms           FormPort — envelope + validate (doc 22)
  tenancy         tenant context (solution_id / org_id)
  audit           append-only audit via DataClient
  module          catalog, ports, ModuleNotPresentException
  data.client     DataClient, DataTransaction, Query, Row
  data.server     PostgresDataServer, (future) other
  cache.client    CacheClient, PubSubClient
  cache.server    ValkeyCacheServer, MemoryCacheServer
  broker          BrokerClient
  realtime        WsGateway, CachePubSubBridge
```

Feature code:

```
com.managemyopz.modules.<feature>
  <Feature>AutoConfiguration     # @AutoConfiguration, conditional on module present
  api                            # REST controllers
  application                    # use cases, validators
  domain                         # entities as values; no JPA annotations required
  adapter                        # maps domain ↔ DataClient rows
```

**No Spring Data JPA in feature modules** for v1. Persistence goes through `DataClient` so `db.type` can change without rewriting services. (Internal Postgres server may use JDBC.)

## 4. HTTP surface

| Prefix | Owner |
| ------ | ----- |
| `/api/v1/opzhub/health` | Kernel |
| `/api/v1/opzhub/meta/modules` | Kernel catalog (enabled modules only) |
| `/api/v1/opzhub/meta/auth` | Kernel/identity public login methods (tiny; pre-company) |
| `/api/v1/opzhub/license/**` | Live resolve, preflight, validate-on-login ([20](20-licensing-site-central.md)) |
| `/api/v1/opzhub/identity/**` | identity (login, OAuth2, face, fingerprint, session, access check) |
| `/api/v1/opzhub/forms/**` | Kernel `FormPort` — field policy + rules + FK meta ([22](22-common-fields-forms-fk.md)) |
| `/api/v1/opzhub/lookup/**` | Kernel lookup router → module `LookupPort` |
| `/api/v1/opzhub/io/**` | Import, export, bulk CUD ([22](22-common-fields-forms-fk.md) §8) |
| `/api/v1/opzhub/mail/**` | Inbox/outbox + inbound from Python ([23](23-mail-send-receive.md)) |
| `/api/v1/opzhub/<feature>/**` | That feature’s controllers |
| `/ws/opzhub` | Kernel WebSocket |

Controllers return kernel envelopes:

```
{ "ok": true, "data": {}, "error": null, "correlation_id": "..." }
```

Failures: `ok: false` plus `error.code`, `kind`, user-safe `msg`/`hint`, optional `fields`, always `correlation_id`. Timeouts use `code: timeout` / `hang_timeout` and **release** the pool slot. Full contract: [24](24-hang-prevention-error-reporting.md). Driver text never reaches the SPA in prod.

Money POST/PUT require header `Idempotency-Key`. Kernel middleware stores the key in cache or a kernel table.

## 5. Module SPI (Java)

On startup:

1. `ModuleCatalog` reads generated imports + `module.yaml` metadata copied as resources.
2. Each module’s `AutoConfiguration` registers controllers, ports, and inbound consumers.
3. Missing optional ports: inject `Optional<DocumentsPort>` or a no-op proxy.

Port examples (kernel interfaces, implemented by modules):

| Port | Implemented by | Used by |
| ---- | -------------- | ------- |
| `IdentityPort` | identity | all (login, session, `require` / row filters) |
| `FormPort` | kernel + module form YAML | SPA/Flutter field kit; no app names in kernel apply |
| `LookupPort` | each module that owns a `res` token | kernel `/lookup/{res}` |
| `IoPort` | kernel + module list commands | import / export / template |
| `BulkPort` | kernel + module writes | bulk create / update / delete |
| `BioMatchPort` | identity (Java client → Python) | identity only; not a public API |
| `LedgerPostingPort` | ledger | inventory, documents |
| `InventoryPort` | inventory | documents, ocr result applier |
| `NotifyPort` | notifications | all (in-app / WS — not SMTP) |
| `MailPort` | mail | sold apps queue send; Java → Python SMTP |
| `MailInboundPort` | ticketing / HR (optional) | mail module after IMAP persist |
| `DocumentsPort` | documents | ocr |
| `OcrJobPort` | ocr | documents UI via REST |

If `ocr` folder is removed, `OcrJobPort` is absent; documents module hides “Extract” without crashing.

## 6. Transactional rules (ACID)

- `DataClient.transaction(isolation, work)` is the only write grouping API.
- Ledger posting and inventory movement that must stay consistent use **one** transaction in the Java engine.
- Default isolation: `READ_COMMITTED`. Posting, period close, and stock allocation that cannot tolerate write skew: `SERIALIZABLE` or explicit locking **documented in the module**.
- Python workers **do not** post ledgers. They publish `ocr.extraction.completed`; an `ocr` or `documents` Java listener validates and writes.

## 7. Realtime

```
Browser ──WS──► WsGateway (Spring)
                    │ subscribe user topics
                    ▼
             CacheClient.subscribe("notify.user.{id}")
                    ▲
             Valkey Pub/Sub (backplane)
                    ▲
      Python worker / other ERP replica publish
```

`CachePubSubBridge` is kernel code. Feature modules only `NotifyPort.publish(userId, payload)`.

WebSocket protocol (design): JSON envelopes from AsyncAPI — `type`, `topic`, `ts`, `payload`. Heartbeat every 25s so Nginx idle timeouts do not drop sockets (proxy timeouts in doc 09).

## 8. Integration with AI engine

Outbound (Java → broker):

```
BrokerClient.publish("ocr.extract", { job_id, document_id, blob_ref, mime, kind, options })
```

`kind` is `image` \| `pdf` \| `doc` \| `html` (from MIME). Unknown kind is rejected in Java before enqueue.

Inbound (results):

- Preferred: worker writes result blob + publishes cache event; Java consumes event or worker calls **internal** Java REST `POST https://opzhub-be-app:8114/api/v1/opzhub/ocr/results` (mTLS, Docker DNS, not public).
- Java validates schema, then `DataClient.transaction` for business tables.

## 9. Configuration binding

Kernel binds `platform.yaml` (doc 08):

```yaml
db:
  type: postgres          # selects DataServer implementation
cache:
  type: valkey
broker:
  type: valkey            # Valkey Streams; kafka reserved / not implemented
```

`DataClientConfig` / `CacheClientConfig` / `BrokerClientConfig` are `switch (type)` factories. Feature `@Service` classes inject `DataClient`, `CacheClient`, and `BrokerClient` only.

## 10. application.yml (spec, not a repo file)

Implementation will ship approximately:

```yaml
spring:
  application:
    name: opzhub-be-app
  threads:
    virtual:
      enabled: true
  servlet:
    multipart:
      max-file-size: 2MB          # large media goes to AI engine / storage

server:
  port: 8114
  ssl:
    enabled: true
    bundle: internal              # keystore from /certs/internal
  shutdown: graceful

erp:
  platform-config: ${PLATFORM_CONFIG_PATH:/platform/config/platform.yaml}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

Datasource **username/password/url are not duplicated** as first-class Spring `spring.datasource.*` for app code. They sit under `erp.db.postgres.*` inside platform YAML and are read by `PostgresDataServer`. (A thin Spring datasource may still be created internally by that server.)

## 11. Controller pattern (spec)

A kernel health controller and a **sample shape** for modules (ledger would own the real one):

- Inject `DataClient`, `CacheClient`.
- No `JdbcTemplate` in the controller.
- No Valkey driver in the controller.

Example responsibility of a module controller: parse request DTO → application service → `DataClient` → envelope. Connection parameters never leak to JSON.

## 12. Security

Identity, RBAC, ABAC, compact login matrix, OAuth2, face, and fingerprint: **[18](18-identity-rbac-abac-oauth2.md)**. Python match algorithms: **[05](05-backend-python-design.md) §13**. Java calls `opzhub-be-core` over mTLS; it does not run ONNX.

- On boot (and `opzhubctl start`), **migrate** applies pending SQL for kernel + present modules; it does not wipe rows ([19](19-db-backup-migrate.md)).
- Method security via `AccessPort.require(app, feature, "u", attrs)` on application services, not only controllers.
- Login/session body is compact: **feature → letter string** (`vcua`) for installed apps only. ABAC stays server-side.
- **FormEnvelope** (`GET /forms/{form_id}`): effective `req` / `mode` / `rules` / `opts` / `fk` / `io` / `bulk` after RBAC+ABAC. Save calls `FormPort.validate` then the module command. Kernel `FormPort` does not `switch` on sold app ids ([22](22-common-fields-forms-fk.md)).
- Import / export / bulk: `IoPort` / `BulkPort` under `/api/v1/opzhub/io/{form_id}/…`. Same `FormPort.validate` per row; export streams; large files are jobs. Letters: export `v`, import/bulk create `c`, bulk update `u`, bulk delete `d`. Money collections may catalog-disable import/bulk.
- **Mail:** Java owns outbox/inbox rows and `MailPort.queue`. SMTP/IMAP/MIME run only in Python. No `JavaMailSender` in feature modules ([23](23-mail-send-receive.md)).
- Writes that reference another row use **Postgres FKs** when the parent is kernel or `requires`; otherwise a logical FK + service check ([22](22-common-fields-forms-fk.md) §6).
- Tenant id from session; `DataClient` may `SET app.tenant_id` for Postgres RLS if enabled in YAML.

## 13. Testing

| Layer | What |
| ----- | ---- |
| Kernel | DataClient against Testcontainers Postgres; CacheClient against Valkey testcontainer or memory server |
| Module | Slice tests with `DataClient` fake in-memory server |
| Composition | Boot with module set A vs set B |

## 14. What gets deleted with a module folder

- Entire `com.managemyopz.modules.<feature>`
- `modules/<feature>/db` migrations
- Generated import line
- REST prefix for that feature

Kernel WebSocket and health remain.
