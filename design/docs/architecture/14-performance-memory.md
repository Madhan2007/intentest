# 14 — Production Performance & Memory Safety

This platform is intended for **production** heavy data, OCR, and realtime. Implementation must treat **bounded resources** and **explicit lifecycle** as kernel contracts, not as afterthoughts. Unbounded caches, leaked connections, and native image buffers are defects.

## 1. Goals

| Goal | Production bar |
| ---- | -------------- |
| Throughput | ERP HTTP stays responsive while OCR runs in workers |
| Latency | Money posts are not blocked by image pipelines |
| Stability | Heap / RSS plateaus under sustained load; no growth after 24h soak |
| Reuse | Pools and buffers are shared in `common/`; modules do not create extra global caches |
| Safety | Every I/O handle, subscription, `Mat`, and buffer has an owner and a close path |

Soak test (design): 8h OCR + ledger traffic; RSS and Java heap must stay within Compose `mem_limit` with headroom ≥ 20%.

## 2. Universal rules (all runtimes)

1. **Bound everything.** Pools, queues, WebSocket sessions, in-memory maps, OCR page counts, and image megapixels have YAML maxima. Exceeding the max fails the request or sheds load; it does not grow RAM.
2. **No unbounded `static` / process-global caches** in modules. Use Caffeine (Java) or `cachetools`/`functools.lru_cache` with `maxsize` (Python), or `CacheClient` with TTL.
3. **Close on all paths.** `try-with-resources` / `async with` / `finally`. Feature code does not open driver connections (clients own pools).
4. **Backpressure.** Broker consume prefetch is finite. FastAPI does not enqueue if the stream/group lag exceeds `broker.backpressure.max_lag`.
5. **Size-limited payloads.** ERP multipart stays small; AI uploads cap pages/pixels (module YAML). Reject before decode.
6. **No thread/task leak.** Cancel on request timeout; Java virtual threads still must not block on unbounded locks; Python tasks must be awaited or cancelled in lifespan shutdown. Deadlines, flock, and hang error codes: [24](24-hang-prevention-error-reporting.md).
7. **Native memory is first-class.** OpenCV, Paddle, Netty, and Postgres off-heap must be released; heap metrics alone are not enough (track RSS in `opzhubctl health`).
8. **Reuse, don’t copy.** Polars/Arrow and Java `ByteBuffer` policies: avoid extra copies of images and result tables. Prefer streaming pipelines over `imread` of huge TIFFs into a single array when the library allows.
9. **Worker recycle.** Python workers: `max_jobs_per_process` then exit (supervisor restarts). Mitigates native allocator fragmentation.
10. **Fail closed on leak detectors in CI.** Netty leak detection `paranoid` in tests; Python `tracemalloc` snapshot diffs on pipeline unit tests.

## 3. Java ERP engine

| Control | Design |
| ------- | ------ |
| Heap | Fixed `-Xms`/`-Xmx` in Compose (no unbounded container). Use G1 or ZGC per JDK 21 guidance; document the choice in the solution overlay. |
| Virtual threads | On for request I/O. **Do not** pin on `synchronized` around JDBC if avoidable; `DataServer` uses pool checkout with timeout. |
| JDBC | Hikari `maximumPoolSize` = YAML `db.postgres.pool_max`; `leakDetectionThreshold` > 0 in staging. Checkout timeout, not infinite wait. |
| HTTP / WS | Cap concurrent WS sessions per node (`realtime.max_sessions`). Idle timeout aligned with Nginx. Drop slow consumers. |
| Caches | Caffeine `maximumSize` + TTL for read models. **Forbidden:** `ConcurrentHashMap` that only grows. |
| JSON | Jackson afterburner optional; do not buffer entire report **or collection** exports in memory — stream (`IoPort`). |
| Classloader | Modules are compile-time mapped, not hot-unloaded; no leaking reload classloaders. |
| Diagnostics | `-XX:+HeapDumpOnOutOfMemoryError`; Micrometer JVM + Hikari + WS gauges. |

Ledger posting: no ORM session cache of the whole period; load pages via `DataClient` with limits.

## 4. Python AI / OCR

| Control | Design |
| ------- | ------ |
| API process | Async only for accept/enqueue **except** identity bio verify: dedicated bounded thread pool. Timeout from `face.profile` (`medium` ~600 ms, `high` ~1500 ms). No Paddle in this process. |
| Worker | Process-per-worker (not threads for Paddle/OpenCV). `OMP_NUM_THREADS` / `OPENCV_FOR_THREADS_NUM` capped. |
| Images / pages | Decode with max dimension; downscale before OCR; `del mat` / `mat.release()` in `finally`. Never keep all pages of a PDF/doc/HTML render in RAM — one page (or a small window) at a time. HTML: `max_html_kb`; no network; headless render only if `ocr.html.render`. |
| Polars | Prefer lazy/streaming scans for tabular parse; `collect` only bounded frames. |
| asyncio | `Queue(maxsize=N)` for internal staging. Lifespan cancels tasks on shutdown. |
| Paddle/Tesseract | Single model instance per worker process (reuse). Do not load a new engine per request. |
| Recycle | `max_jobs_per_process` (e.g. 50–200) then exit; Compose `restart: on-failure`. |
| Uploads | Spool to `ai-staging` volume; do not hold multipart in RAM. |
| HTTP client to ERP | Shared `httpx.AsyncClient` with pool limits; close on shutdown (no per-job client). |

## 5. Frontend (SPA)

- Unsubscribe `WsClient` and abort `fetch` on unmount (`AbortController`).
- Virtualize `DataTable` / `EditableGrid` (windowed rows). Do not mount 50k DOM cells.
- ImageDrop / OCR drop: preview via object URL **revoked** after upload or unmount (PDF/doc/HTML use a type icon, not a decoded bitmap in the browser).
- **`gui.mode: lite`:** do not fetch `branding/rich` or module `assets/rich`; no icon pack.
- **`gui.mode: rich`:** lazy-load below-the-fold art; `max_inflight` image fetches; WebP/SVG; revoke URLs on skin switch to lite.
- Face login PAD (WASM/MediaPipe) on a **worker**; do not freeze the React/Flutter UI. Do not upload if client PAD fails.
- No module-level arrays that accumulate notifications forever; ring buffer with max length.
- Code-split per module so unused features are not in memory (plugin-play).

## 6. Valkey / Postgres / Nginx

| Component | Production bound |
| --------- | ---------------- |
| Valkey | `maxmemory` + `noeviction` or `volatile-ttl` (see broker doc). `maxclients` set. AOF without unbounded growth policy documented. |
| Postgres | `shared_buffers` sized to EC2; `max_connections` ≥ app pools + workers + margin; statement_timeout on app roles. |
| Nginx | `proxy_buffering` off only for WS; API locations use bounded `proxy_buffers`. `worker_connections` set. `client_max_body_size` already capped. |
| Valkey Streams | Trim `MAXLEN`; do not let OCR/mail payloads grow RAM without bound. |

## 7. YAML knobs (kernel)

```yaml
performance:
  java:
    heap_mb: 1024
    hikari_leak_ms: 30000
  python:
    worker_max_jobs: 100
    opencv_threads: 2
    max_in_flight_jobs: 16
    bio_threads: 4
    vision_quality: auto            # see python.vision.quality
    bio_timeout_ms: 600             # medium; high ~1500
    bio_max_inflight: 8
  realtime:
    max_sessions_per_node: 5000
  gui:
    rich_max_inflight: 4
  broker:
    prefetch: 4
    backpressure_max_lag: 10000
```

`opzhubctl doctor` warns if `heap_mb` + worker RSS budget exceeds the EC2 instance without 20% headroom.

## 8. Observability for leaks

Expose (implementation later):

- JVM: heap used, GC pause, Hikari active/idle/pending, WS count, Netty direct memory.
- Python: RSS, `tracemalloc` optional, queue depth, in-flight pages.
- Valkey: `used_memory`, stream length, blocked clients.
- Alerts: heap > 85% for 10m; RSS growth linear over 2h; Hikari pending > 0 sustained; stream lag > backpressure max.

## 9. Code review checklist (modules)

A module is not production-ready if it:

- Starts a thread/timer without shutdown in `AutoConfiguration` / FastAPI lifespan.
- Subscribes to Valkey Pub/Sub without unsubscribe on bean destroy.
- Loads an entire table into a `List` for UI.
- Catches exceptions around native OCR without releasing `Mat`.
- Creates a new HTTP/DB client per request.

Kernel reviewers reject those PRs. Reuse kernel clients and field/grid virtualization instead of new caches.
