# 24 — Hang prevention and error reporting

Every I/O path has a **deadline**, a **bound**, and a **close path**. Failures return a **stable error contract** that operators and developers can grep — users see a safe, understandable message plus a **correlation id**, not a stack dump.

Related: jobs already use `flock` + `timeout --kill-after` ([16](16-jobs-crontab.md)); pools and RSS ([14](14-performance-memory.md)). This document is the **cross-runtime** contract. Design only.

## 1. Goals

| Goal | Rule |
| ---- | ---- |
| No hang | No infinite wait on DB, cache, broker, HTTP, SMTP/IMAP, WS, file lock, or native OCR |
| No leftover | Timeout **kills the work** and **releases** the pool slot, lock, `Mat`, socket, object URL |
| No pile-up | Busy lock → skip (exit 75), do not queue a second overlapping job |
| Debuggable | Same `correlation_id` on HTTP, logs, job line, WS event, and the UI |
| Understandable | Machine `code` + i18n `msg` + optional `hint`; no driver text in prod UI |
| No leak | Secrets, passwords, face frames, SMTP bodies never in error JSON or INFO logs |

## 2. Preventive mechanisms (avoid hanging resources)

### 2.1 Hard rule

**Every outbound call has a timeout.** Infinite `0` is illegal in `profile: prod` (`opzhubctl doctor`). A timeout must:

1. Cancel the work (virtual thread / asyncio task / process group).
2. Return the connection to the pool (or close it if dirty).
3. Emit error `code: timeout` with `res` (which resource hung).
4. Never leave a lock held after the process is dead (stale PID reap — already in `run-job.sh`).

### 2.2 Resource map

| Resource | Prevent hang | On timeout / fail |
| -------- | ------------ | ----------------- |
| HTTP (FE → origin) | `HttpClient` deadline; `AbortController` / Flutter cancel on unmount | Toast + `correlation_id`; no retry storm |
| HTTP (Java ↔ Python) | `http.client_timeout_ms`; mTLS pool limits | `503` / `timeout`; fail closed for login/mail sync |
| License hub | `license.live_ms` debounce + HTTP timeout; one in-flight | Generic fail; do not block typing forever |
| JDBC / asyncpg | Checkout timeout + `statement_timeout` + `idle_in_transaction_session_timeout` | `DataUnavailable` / `timeout`; rollback |
| Hikari / pool | `pool_max`, `leakDetectionThreshold` | Log leak + metric; doctor warn |
| Valkey | Command timeout; `maxclients` | `CacheUnavailable`; session fail closed |
| Broker | Prefetch + ack deadline + `backpressure_max_lag` | Do not enqueue; DLQ after N retries |
| Cron / scripts | `opzhub-run-job` `flock -n` + `timeout --kill-after=15s` | Exit 75 skip / 124 hang; SIGKILL process group |
| Stale job lock | Reap PID that is not alive ([16](16-jobs-crontab.md) wrapper) | Log `stale pid reaped` |
| WebSocket | Heartbeat 25s; idle drop; `max_sessions` | Close; client reconnect with backoff |
| SMTP / IMAP | `mail.send.sync_timeout_ms`; IMAP poll cap | Job `failed`; do not leave IMAP SELECT hung |
| OCR / bio | Page window; `bio_timeout_ms`; `bio_max_inflight` | Release `Mat`; recycle worker |
| Import / export | `io_max_rows`; stream; job timeout | Partial result + row errors |
| FE object URLs / WS | Unmount abort + revoke | No leaked blob URLs |
| File descriptors | Kernel clients only; no per-request socket | Lifespan / bean destroy close |

### 2.3 Deadline budget (propagate)

Incoming request gets `deadline_ms` (YAML default per class). Downstream calls use **remaining** budget (never a fresh 60s on each hop).

```
FE  15s  →  Java 12s  →  Python 8s  →  SMTP 5s
```

If remaining < 50 ms → fail immediately (`timeout`, `hint: budget`). Header (internal): `X-Opzhub-Deadline` (epoch ms). Browser does not send a forged deadline that *extends* the server cap.

### 2.4 Classes of request (YAML)

```yaml
hang:
  http_ms: 15000              # public API
  java_python_ms: 8000        # mTLS hop
  db_checkout_ms: 3000
  db_statement_ms: 20000      # money post may use module overlay
  cache_ms: 500
  broker_ack_ms: 10000
  mail_smtp_ms: 8000
  mail_imap_poll_ms: 30000
  job_default_s: 300          # run-job.sh; already OPZHUB_JOB_TIMEOUT
  job_kill_after_s: 15
  ws_idle_s: 90
  lock_stale_s: 3600          # doctor: lock file older + dead pid
```

Money `SERIALIZABLE` posts use a **documented** longer `db_statement_ms` on that command only — not a global infinite.

### 2.5 Watchdog (operator)

`opzhubctl status` / health:

- Service PID alive but no `/healthz` within `hang.http_ms` → `degraded` + `code: hung_probe`.
- Job lock present, PID dead → reap (wrapper already does this at start).
- Job lock present, PID alive longer than `2 × timeout` → log `hung_job`, do **not** SIGKILL from `status` (operator `opzhubctl stop` / next timeout).
- Hikari active == max for > `hikari_leak_ms` → metric + doctor warn.
- Broker lag > `backpressure_max_lag` → shed new OCR/mail enqueue.

Compose `mem_limit` + worker recycle ([14](14-performance-memory.md)) still apply.

### 2.6 What modules must not do

- `Thread.sleep` / `asyncio.sleep` without a ceiling in a request path.
- `synchronized` / lock wait without timeout.
- `flock` without `-n` on crontab (waiting hangs the hour).
- Catch-all `except: pass` that swallows cancel/timeout.
- Start a timer/thread/IMAP IDLE without lifespan cancel.

## 3. Error reporting (understandable + debuggable)

### 3.1 Wire envelope (all public JSON APIs)

```json
{
  "ok": false,
  "data": null,
  "error": {
    "code": "timeout",
    "kind": "timeout",
    "msg": "The request took too long and was stopped.",
    "i18n": "err.timeout",
    "hint": "Try again. If it repeats, give support this id.",
    "res": "db",
    "retry": true,
    "retry_after_s": 2,
    "fields": []
  },
  "correlation_id": "c_7f3a9c2e"
}
```

| Field | Who uses it |
| ----- | ----------- |
| `code` | Stable token: `timeout`, `hang_timeout`, `busy`, `validation`, `conflict`, `fk`, `denied`, `unauth`, `not_found`, `unavailable`, `internal`, `license`, `compose` |
| `kind` | Bucket for FE: `timeout` \| `validation` \| `conflict` \| `denied` \| `unavailable` \| `internal` |
| `msg` | **User-safe** sentence (or resolve `i18n` in kernel). Never JDBC/SMTP text in prod |
| `hint` | What to do next |
| `res` | Which resource: `db`, `cache`, `broker`, `http`, `smtp`, `imap`, `ocr`, `job`, `ws`, `lock` |
| `fields` | Form errors `{ "n", "err" }` ([22](22-common-fields-forms-fk.md)) |
| `retry` | FE may retry once if true (honor `retry_after_s`) |
| `correlation_id` | Same id as logs — **always present**, success or fail |

HTTP status still set: 408/504 timeout, 409 conflict, 422 validation, 401/403, 429 busy, 503 unavailable, 500 internal. Body `code` is the source of truth for the SPA.

### 3.2 Layers of detail (do not mix)

| Audience | Sees | Never sees |
| -------- | ---- | ---------- |
| End user (SPA/Flutter) | `msg`, `hint`, `fields`, copyable `correlation_id` | Stack, SQL, host:port, SMTP banner, ABAC reason in prod |
| Operator (`opzhubctl logs`, job log) | JSON line: `code`, `res`, `correlation_id`, `duration_ms`, `svc`, `job`, `tenant_id` | Passwords, tokens, face frames, full mail HTML |
| Developer (`profile: dev` or `security.errors.debug: true`) | Extra `debug.where`, `debug.cause` (mapped enum, not raw SQL) | Still no secrets |

`debug` is **omitted** in prod JSON unless `security.errors.debug` (forbidden in prod doctor). Operators use logs, not a fatter API.

### 3.3 Log line (every runtime)

```json
{
  "ts": "2026-09-01T04:22:00Z",
  "lvl": "ERROR",
  "svc": "opzbe",
  "code": "timeout",
  "res": "db",
  "correlation_id": "c_7f3a9c2e",
  "tenant_id": "acme",
  "user_id": "u_01",
  "route": "POST /api/v1/opzhub/hr/employees",
  "duration_ms": 20041,
  "job": null
}
```

Same keys in Python, wrappers (`run-job.sh` already prints `job=` `rc=` `timeout=`), and Nginx `X-Correlation-ID`. If the client omitted the id, the first hop **creates** it.

Map driver exceptions **once** in `DataServer` / `MailSendServer` / HTTP client — feature modules throw kernel errors only ([07](07-data-cache-client-server.md)).

### 3.4 Frontend (kernel only)

`common/frontend` + `common/mobile`:

- `ErrorState` / toast: `msg` + `hint` + **Copy id** (`correlation_id`).
- Field errors bind to `FieldError` by `n`.
- `kind=timeout` → one automatic retry if `retry` (backoff); then stop.
- `kind=validation` → no toast-only; show fields.
- No `alert(e.stack)`. No module-specific error widgets for HTTP.

### 3.5 Hang-specific codes

| `code` | When |
| ------ | ---- |
| `timeout` | Deadline hit; resource released |
| `hang_timeout` | Kill-after fired (job 124, or HTTP after TERM ignored) |
| `busy` | Lock held / pool exhausted / `flock -n` skip (75) — **not** a hang |
| `cancelled` | Client abort / unmount |

Job log must include `correlation_id` when the job was triggered by an API (import, mail send). Cron-only jobs mint an id at start and print it on `start` / `end` / `skip` lines.

### 3.6 Jobs and scripts

| Exit | Meaning | UI / crontab |
| ---- | ------- | ------------ |
| 0 | ok | |
| 75 | skipped, already running | not an error page; metric `job_skip` |
| 77 | refused (root / layout) | operator |
| 124 | timeout (GNU) | `code: hang_timeout`, `res: job` |
| 2–5 | `opzhubctl` composition / health / migrate / usage ([06](06-scripts-design.md)) | |

## 4. Kernel ownership

| Piece | Where |
| ----- | ----- |
| Envelope + `correlation_id` filter | Java `web`, Python `app_factory` |
| Driver → `code` map | `DataServer`, cache/broker/mail servers |
| FE display | `fields/feedback/ErrorState` + `api/errors.ts` |
| Job timeout / flock | `infra/wrappers/run-job.sh` (implemented) |
| YAML `hang.*` | [08](08-configuration-yaml.md) |

Sold apps do not invent a second error JSON or a second HTTP client without deadlines.

## 5. What must not happen

- Infinite HTTP/DB/SMTP wait in prod.
- Timeout that leaves a checked-out connection or IMAP SELECT.
- Overlapping cron because the previous hour is hung (must skip 75 or kill-after).
- User-facing `PSQLException: ...` or Python traceback.
- Error body without `correlation_id`.
- Logging the request password, license secret, or mail body at ERROR.
- Retry loops without `retry_after_s` / max 1 on the client for timeouts.
- Swallowing `CancelledError` / interrupt.
