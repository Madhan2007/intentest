# 11 — Async Broker Selection (Valkey default; Kafka optional later)

## 1. Decision (this drop)

Job queues use **`BrokerClient`**. **This implementation drop uses Valkey Streams only.** Kafka stays an **optional** future `BrokerServer`. Do **not** implement `KafkaBrokerServer`, `aiokafka`, a Kafka Compose service, or `broker.type: kafka` until a later drop.

```yaml
broker:
  type: valkey          # v1 — reuse existing Valkey Streams
  # type: kafka         # OPTIONAL, not implemented — reserved swap path
  # type: memory        # tests only — forbidden in prod
```

`opzhubctl doctor` **fails** if `broker.type` is `kafka` (not implemented). Feature code always calls `BrokerClient`. Workers never import Valkey or Kafka drivers.

Compose does not start a third queue product in this drop.

## 2. Three different messaging jobs (do not collapse them)

| Job | Pattern | v1 home |
| --- | ------- | ------- |
| Session + TTL + locks | KV get/set | `CacheClient` → Valkey |
| Live UI fan-out | Pub/Sub (fire-and-forget, no ACK) | `CacheClient.subscribe` → Valkey Pub/Sub |
| OCR / image / mail jobs | Competing consumers, ACK, retry, DLQ | `BrokerClient` → **Valkey Streams** |

Using **one Valkey Pub/Sub channel** for OCR jobs would be wrong: every worker would see every message, there is no ACK, and a reconnect loses in-flight work. Reuse Valkey, but use **Streams** (consumer groups) for the broker role.

## 3. Default for this platform (single EC2)

| Setting | Choice | Why |
| ------- | ------ | --- |
| `broker.type` | `valkey` | No extra container; enough for OCR/mail job cards on one host |
| Mechanism | Valkey **Streams** + consumer group per worker class (`ocr.ingest`, `ocr.extract`, `image.deskew`, `mail.send`) | ACK, pending entries, `XCLAIM` on crash |
| Same process as cache? | **Yes, allowed** | Isolate with `broker.valkey.db` or key prefix `stream:` and a dedicated client pool |
| Kafka | **Optional later** | Replay, long retention, or many consumer groups — **not built now** |

## 4. Capability matrix

| Capability | Valkey Streams (**v1**) | Kafka (**later**) | memory |
| ---------- | ---------------------- | ----------------- | ------ |
| Competing consumers | yes (consumer groups) | yes | no |
| ACK / retry | yes (`XACK`, PEL) | yes (offset commit) | limited |
| Dead-letter | implement in adapter (retry count → `stream:dlq:*`) | yes (DLT topic) | no |
| Replay history | short (trimmed `MAXLEN`) | **strong** (retention) | no |
| Ops weight on one EC2 | **lowest** (already running) | highest (KRaft broker + disk) | n/a |
| Horizontal pub/sub for WS | **not this adapter** — still CacheClient | not used for WS | n/a |
| TLS | yes | yes (when added) | n/a |

Production OCR/mail profile **requires**:

```yaml
broker:
  required_capabilities: [competing_consumers, ack, retry]
```

`memory` fails that check. Valkey Streams pass. Kafka would pass **after** it is implemented.

## 5. Kafka (optional — do not implement now)

Keep the **port**: `BrokerClient` + `broker.type` factory. A later drop may add `KafkaBrokerServer` without changing OCR/mail workers.

Choose `broker.type: kafka` **only in that later drop** when:

- Jobs must be **replayable** days later (audit, re-OCR after a model upgrade).
- Several independent consumer groups read the same topic (indexer, warehouse, OCR).
- Event volume or retention will outgrow a single Valkey `maxmemory`.
- The customer already runs Kafka and wants one bus.

Until then:

- Do not add a `kafka` Compose service or `kafka-data` volume to the runnable stack.
- Do not set `OPZHUB_ENABLE_KAFKA` (ignored / unused).
- Do not vendor Kafka client libraries in the kernel BOM.
- Valkey **still** runs for cache and WS even if Kafka is added later.

Kafka would still be a **BrokerServer**, not a replacement for PostgreSQL or for Valkey sessions/WS.

## 6. Valkey dual-use rules (mandatory if `broker.type: valkey`)

1. **Three connection classes** to the same host (may share process, not sockets):
   - Cache command connection (GET/SET/HSET)
   - Cache Pub/Sub connection (WS backplane)
   - Broker Streams connection (`XADD` / `XREADGROUP` / `XACK`)
2. **Key isolation:** cache keys `sess:`, `idem:`, `ws:`; streams `stream:ocr.extract`, `stream:image.deskew`, `stream:mail.send`, `stream:dlq:*`.
3. **Eviction:** production Valkey for mixed use must **not** use `allkeys-lru`. Sessions use TTL; streams are trimmed explicitly (`MAXLEN` / `MINID`). Prefer `noeviction` or `volatile-ttl`.
4. **Optional split:** `broker.valkey.host` may point at a second Valkey if OCR floods memory. Same `BrokerClient` API.
5. **Persistence:** AOF (already specified for cache) also protects streams. `opzhubctl doctor` warns if AOF is off while `broker.type: valkey`.

## 7. BrokerClient contract (Java + Python + scripts)

| Operation | Semantics |
| --------- | --------- |
| `publish(queue, payload, opts)` | Durable add (`XADD` in v1) |
| `consume(queue, handler)` | Competing consumer; handler ACK only on success |
| `retry_or_dlq(msg, error)` | Adapter maps to retry delay or DLQ |
| `ping()` | Broker connectivity |
| `ensure_topology()` | Create stream/group at boot |

`queue` names come from `module.yaml` `provides.workers` (example: `ocr.extract`). Removing the OCR folder means no consumer and no topology for that queue.

Payload is JSON with `job_id`, `correlation_id`, `tenant_id`. **No image bytes on the bus** — only blob refs.

## 8. Kafka adapter notes (later drop only)

When Kafka is implemented:

- `KafkaBrokerServer` uses TLS (`security.protocol=SSL` or `SASL_SSL`). See [12](12-transport-security-tls.md).
- Topics: `erp.<queue>` with `replication.factor` 1 on single-node Compose; RF=1 is **not** multi-AZ HA.
- OCR commands use **delete** retention (time or size), not compaction.
- Workers commit offsets **after** pipeline success (at-least-once). Java inbound APIs stay **idempotent** on `job_id`.
- Compose profile `kafka` starts KRaft Kafka only when `broker.type: kafka`. Valkey **still runs** for cache/WS.

Do not write these adapters or Compose blocks in the current implementation.

## 9. Compose impact (this drop)

| `broker.type` | Extra services |
| ------------- | -------------- |
| `valkey` | **none** (use existing `valkey`) — **ship this** |
| `kafka` | not started; doctor fail until a later drop |
| `memory` | none (forbidden in prod) |

AI workers `depends_on` Valkey only.

## 10. Swap procedure (later)

1. Implement `KafkaBrokerServer` satisfying `BrokerServer`.
2. Register factory key `kafka`.
3. Add Compose profile `kafka` + TLS listener.
4. Set `broker.type: kafka` and `broker.kafka.*`.
5. Run `opzhubctl doctor` and worker tests.
6. Do not change OCR/mail `BrokerClient.publish` call sites.
