# 07 — Data & Cache Client–Server Model

## 1. Why this exists

Today the platform stores durable ERP data in **PostgreSQL** and coordination/session state in **Valkey**. Tomorrow a customer may require a different engine (another RDBMS, a managed cache, or an in-memory test double).

Application code in **Java, Python, and scripts** must not import driver-specific types. It talks to a **client**. YAML selects which **server** implementation is constructed behind that client.

This is a **client–server communication model inside the process**, not a new network protocol for browsers. Browsers still use HTTP/WebSocket to Nginx.

```
  ┌───────────── Feature code (Java / Python / scripts) ─────────────┐
  │  DataClient     CacheClient     BrokerClient                     │
  └──────┬──────────────┬────────────────┬───────────────────────────┘
         ▼              ▼                ▼
   DataServer     CacheServer      BrokerServer
   Postgres       Valkey KV/PubSub Valkey Streams (v1)
   Memory         Memory           Kafka (optional later — not implemented)
                                   Memory (tests)
         │              │                │
         ▼              ▼                ▼
    PostgreSQL TLS   Valkey TLS     same Valkey TLS
```

Flags:

```yaml
db:
  type: postgres          # factory key
  router: off             # off | company (hub only; see §1.1)
cache:
  type: valkey
broker:
  type: valkey            # Valkey Streams — v1
  # type: kafka           # optional later; doctor fails until implemented
```

Change the flag + connection block; do **not** change controllers, pipelines, or CLI commands.

### 1.1 DataRouter (central hub only)

Same `DataClient` API. The kernel picks **which Postgres pool** after login:

| Company `data` | Pool |
| -------------- | ---- |
| `hub` | Shared hub cluster (`company_id` / RLS) |
| `customer` | Per-company TLS pool to that site’s `db_host` |

`router: company` on `deploy.mode: central`. Site appliances use `router: off` and one `db.postgres` block. Browser never sees DSNs. Detail: [20](20-licensing-site-central.md).

## 2. Design rules

1. **One client type per concern** (`DataClient`, `CacheClient`, `BrokerClient`). No `PostgresClient`, `ValkeyClient`, or `KafkaProducer` in features.
2. **Servers are swappable adapters.** They own connection pools, retries, and dialect.
3. **Capabilities are explicit.** If a server cannot provide transactions, the factory fails at boot when `db.required_capabilities` includes `acid_transactions`.
4. **Identical semantics** across Java and Python (method names may be idiomatic; behavior must match the contract below).
5. **Tests use `memory` servers.** CI for modules should not require Postgres unless the test is labeled `integration`.
6. **No leaky abstractions.** `Row` is a map of typed values, not a JDBC `ResultSet` or `asyncpg.Record`.

## 3. DataClient contract

Logical operations (both languages):

| Operation | Semantics |
| --------- | --------- |
| `ping()` | Connectivity |
| `execute(statement, params)` | DML/DDL wrapper; statement is **dialect-neutral AST or named command**, not a raw Postgres string in feature code |
| `query(statement, params) -> list[Row]` | Read |
| `query_one` | Read one or empty |
| `transaction(isolation, fn)` | ACID boundary |
| `batch(statements)` | Same connection, optional transaction |
| `listen_notify` | Optional capability (Postgres NOTIFY); not required for v1 if Pub/Sub is cache-only |

### 3.1 Statements are not raw SQL in features

Feature modules call **named commands** registered by the module:

```
data.command("ledger.insert_journal", params)
data.query("ledger.list_journals", filters)
```

The **Postgres server** maps those names to SQL files under `modules/ledger/db/commands/` or to a query catalog. A future server maps the same names to another dialect.

Alternatively (allowed): a small **query AST** (`Select`, `Insert`, `Update`, `Delete`, `From`, `Where`) implemented by each DataServer. v1 recommendation: **named commands + AST for simple CRUD**; no string concatenation of SQL in Java/Python services.

Kernel may use a narrow escape hatch `data.raw(sql)` **only inside a DataServer implementation or a migration runner**, never in `com.managemyopz.modules.*` application services.

### 3.2 Transactions

```
data.transaction(Isolation.READ_COMMITTED, ctx -> {
  ctx.command("inventory.reserve", ...);
  ctx.command("ledger.post", ...);
  return result;
})
```

Rollback on exception. Nested transactions: savepoints if the server capability `savepoints` is true; otherwise forbid nesting at client level.

### 3.3 Tenant and actor context

`DataClient.with_context({ tenant_id, user_id, correlation_id })` must propagate to the server. Postgres implementation sets GUCs or always includes `tenant_id` in commands. Memory implementation filters rows.

### 3.4 Migrations

Migration runner (scripts) uses the **same** `db.type`. SQL files in `modules/*/db` are **Postgres dialect in v1**. When `db.type != postgres`, either:

- ship a second dialect folder `db/postgres/`, `db/<type>/`, or
- refuse boot if migrations are SQL-only and type is not postgres.

Document this in `platform.yaml` as `db.migrations.dialect: postgres`. v1 production lock: `db.type: postgres` with SQL migrations. Memory server uses schema objects in-process and applies a subset of commands for tests.

### 3.5 Foreign keys (required places)

Postgres production **requires** `relational_constraints`. Use a real `FOREIGN KEY` when the parent table is kernel or listed in this module’s `requires` (identity grants, created_by, ledger account/currency, inventory item/location, same-module line→header). Prefer composite `(tenant_id, id)` so FKs cannot cross tenants. Default `ON DELETE RESTRICT`; `CASCADE` only for owned children in the **same** module.

Do **not** add `REFERENCES` to a table that lives in an **optional** sold app (HR id on a ticket). Store the id and validate in the application service if that module is loaded. UI pickers are kernel `lookup` / `lookup-multi` driven by FormEnvelope `fk` ([22](22-common-fields-forms-fk.md) §6).

`PostgresDataServer` maps FK violations to kernel `Conflict` (field `err: fk`). `MemoryDataServer` emulates the same checks in unit tests; it does not parse SQL `REFERENCES`.

## 4. DataServer implementations (v1)

| `db.type` | Class | Use |
| --------- | ----- | --- |
| `postgres` | `PostgresDataServer` | Production |
| `memory` | `MemoryDataServer` | Unit tests, kernel-only demos |

**Future (not implemented):** `mysql`, `cockroach`, `sqlserver` — new server package, same client.

### 4.1 PostgresDataServer

- Pool: HikariCP (Java), asyncpg pool (Python).
- SSL flags from YAML.
- Bind parameters only; no interpolation.
- Health: `SELECT 1`.
- Optional RLS: `SET app.tenant_id`.

Connection YAML lives under `db.postgres.*` and is **ignored** if `db.type` is not `postgres`.

### 4.2 MemoryDataServer

- Tables as concurrent maps keyed like SQL tables registered by tests/modules.
- Transactions as mutex + copy-on-write or thread-local staging.
- Enough for field-level service tests; not a Postgres emulator.

## 5. CacheClient contract

| Operation | Semantics |
| --------- | --------- |
| `get(key)` / `set(key, value, ttl)` | Opaque bytes or JSON strings |
| `delete(key)` | |
| `set_if_absent` | Session lock / idempotency |
| `incr` | Rate counters |
| `hash_get/set` | Session fields |
| `publish(topic, payload)` | Pub/Sub |
| `subscribe(topic_pattern, handler)` | Backplane |
| `ping()` | |

Session store: `session:{id}` with TTL. Idempotency: `idem:{key}` with TTL.

WebSocket fan-out: modules never subscribe in feature code except through `NotifyPort` / kernel bridge.

## 6. CacheServer implementations (v1)

| `cache.type` | Class | Use |
| ------------ | ----- | --- |
| `valkey` | `ValkeyCacheServer` | Production (Redis protocol) |
| `memory` | `MemoryCacheServer` | Tests; **single process only** — not valid for multi-replica WS |

**Future:** `redis` (if distinct from Valkey), `elasticache`, `dragonfly` — as long as Pub/Sub semantics are documented.

If `cache.type: memory` and replica count > 1, **boot must fail** (platform validator).

### 6.1 ValkeyCacheServer

- Java: official Valkey/Redis client.
- Python: valkey-py or redis-py in Valkey-compatible mode.
- Separate logical DBs or key prefixes: `sess:`, `idem:`, `ws:`, `job:`.
- Pub/Sub connection **must not** share the request connection (subscribe connections block).

YAML under `cache.valkey.*` ignored when type is not `valkey`.

## 7. Broker (same pattern, separate flag)

OCR jobs are **not** cache Pub/Sub messages. Use `BrokerClient` / `BrokerServer`. **This drop: Valkey Streams on the existing Valkey.** Kafka is optional later and is **not implemented**. Full record: [11](11-broker-selection.md).

| `broker.type` | This drop |
| ------------- | --------- |
| `valkey` | **Ship.** Streams + consumer groups; reuse cache host with isolated prefix/DB |
| `kafka` | Reserved; `doctor` fails — do not implement |
| `memory` | Tests only |

Workers and Java `BrokerClient.publish` share queue names from `module.yaml` `provides.workers`.

Drivers use **TLS** (Valkey TLS). Kafka SSL applies only if Kafka is added later. See [12](12-transport-security-tls.md).

## 8. Factory algorithm (both languages)

```
load platform.yaml
validate schema
db_server = DataServerRegistry.get(db.type)
assert db_server.capabilities ⊇ db.required_capabilities
inject credentials for that type only
wrap with DataClient
on failure: abort boot (do not fall through to another type)
```

Unknown `db.type` is a **fatal** configuration error.

## 9. Capability matrix

| Capability | postgres | memory | valkey | cache memory |
| ---------- | -------- | ------ | ------ | ------------ |
| acid_transactions | yes | limited | n/a | n/a |
| relational_constraints | yes | no | n/a | n/a |
| pubsub_horizontal | n/a | n/a | yes | no |
| ttl_keys | n/a | n/a | yes | yes |
| sql_migrations | yes | no | n/a | n/a |

ERP production profile **requires**:

```yaml
db:
  type: postgres
  required_capabilities: [acid_transactions, relational_constraints]
cache:
  type: valkey
  required_capabilities: [pubsub_horizontal, ttl_keys]
broker:
  type: valkey                 # Valkey Streams; kafka not implemented (doc 11)
  required_capabilities: [competing_consumers, ack, retry]
```

## 10. Java vs Python alignment

| Concern | Java | Python |
| ------- | ---- | ------ |
| Config class | `PlatformProperties` | `opzhub_kernel.settings` |
| Data interface | `DataClient` | `DataClient` protocol |
| Cache interface | `CacheClient` | `CacheClient` protocol |
| Broker interface | `BrokerClient` | `BrokerClient` protocol |
| YAML keys | **identical** | **identical** |
| Row types | `Map<String, Object>` with typed getters | `dict` with converters |
| Async | Virtual threads + blocking JDBC in server | `async` client; Postgres server uses asyncpg |

Feature authors should be able to read one contract and implement in either language.

## 11. Error model

Clients throw kernel errors (never driver errors) to features:

| Error | When |
| ----- | ---- |
| `Timeout` / `HangTimeout` | Checkout, statement, or kill-after ([24](24-hang-prevention-error-reporting.md)) |
| `DataUnavailable` | Pool exhausted, network |
| `Conflict` | Unique violation **or FK violation** mapped |
| `NotFound` | query_one empty if required |
| `TransactionFailed` | rollback |
| `CacheUnavailable` | Valkey down — **sessions fail closed** |
| `UnsupportedCapability` | Wrong server type for operation |

Ledger modules must treat `CacheUnavailable` as non-fatal for posting **if** posting does not depend on cache; they must treat it as fatal for login/session.

## 12. Security

- Credentials only in env / secret files, referenced by YAML (`password: ${POSTGRES_PASSWORD}`).
- Python DB role ≠ Java DB role (doc 05).
- Cache AUTH token in YAML `cache.valkey.password`, plus **TLS** (`cache.valkey.tls: true` in prod).
- No client logs of row payloads at INFO for money tables.
- All store connections use TLS in prod ([12](12-transport-security-tls.md)).

## 13. Swap procedure (tomorrow)

1. Implement `FooDataServer` satisfying `DataServer`.
2. Register factory key `foo`.
3. Provide `db/foo` migrations or a translator.
4. Set `db.type: foo` and `db.foo.*` connection block.
5. Run `opzhubctl doctor` and module integration tests.
6. Do not change frontend, controllers, or pipelines.

Same for `cache.type`. Same for `broker.type` **later** (Valkey Streams ↔ Kafka without changing OCR workers). **Do not implement Kafka in this drop.**
