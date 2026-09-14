# 05 — Backend Python Design (Media, OCR, biometric match & mail I/O)

## 1. Role

FastAPI is the **media, OCR, biometric-match, and mail-protocol engine**: accept job metadata, enqueue OCR/image work, expose job status, run **workers** for OpenCV/Paddle, run **login-class** face/fingerprint pipelines for identity, and run **SMTP/IMAP/MIME** for send and receive ([23](23-mail-send-receive.md)).

It shares the same **DataClient / CacheClient / BrokerClient** contracts as Java (language-specific implementations, identical YAML flags).

Python **never issues sessions**. Identity Java remains the only AuthN authority ([18](18-identity-rbac-abac-oauth2.md)). Java **never** speaks SMTP/IMAP; it calls Python.

## 2. One service, two supervised processes

| Process | Docker service | Duty |
| ------- | -------------- | ---- |
| API | `opzhub-be-core` | Async HTTP `/api/v1/ai/*`, cheap validation, enqueue, status reads |
| Worker | `opzhub-be-core` | Consume broker, run pipelines, publish progress |

**Never** run deskew/OCR inside the API event loop beyond `asyncio.to_thread` for trivial CPU; the design default for documents is **always enqueue**. **Exception:** identity biometric verify is **login-class** (§13): bounded ONNX/OpenCV on a dedicated thread pool with a hard timeout — not the OCR broker. Native memory and worker recycle rules: [14 — Performance & Memory](14-performance-memory.md).

## 3. Kernel layout

```
common/python/opzhub_kernel/
  app_factory.py          create_app() → FastAPI
  settings.py             load platform.yaml
  discover.py             load module plugins
  data/client.py
  data/servers/postgres.py
  cache/client.py
  cache/servers/valkey.py
  broker/
  workers/base.py
  mail/                   # MailClient, Smtp/Imap servers, mime (doc 23)
```

Entrypoints (implementation later):

- `services/ai/main.py` → `create_app()`
- `services/ai/worker.py` → `run_all_registered_workers()`

## 4. FastAPI application factory

`create_app()`:

1. Load settings (`db.type`, `cache.type`, `broker.type`).
2. Construct `DataClient`, `CacheClient`, and `BrokerClient` via factories (no module imports of `asyncpg` or `valkey` outside servers). Do not add `aiokafka` in this drop.
3. Mount kernel routers: `/api/v1/ai/health`, `/api/v1/ai/meta/modules`.
4. `discover_modules()` → each `plugin.register(app, registry)`.
5. Lifespan: open client pools over TLS; close on shutdown.
6. Bind HTTPS `:8117` with the internal certificate (Uvicorn/`ssl_certfile`) when `security.tls.mode: required`.

CORS is unnecessary when the browser only talks to Nginx on the same **HTTPS** origin. If opened, restrict to solution origins.

## 5. Async route pattern (spec for later `main.py`)

A kernel-compliant route:

1. Parse metadata with Pydantic (document id, mime, options). **No raw image bytes in the JSON body for large files** — accept upload to a staging path or `multipart` then immediately persist via `StorageClient` / volume and enqueue.
2. Create `job_id`, write job row through `DataClient` **or** job hash through `CacheClient` (TTL) plus durable row if `documents` exists via internal ERP HTTP.
3. `BrokerClient.publish(queue, command)`.
4. Return `{ job_id, status: "queued" }` immediately.

Workers:

1. `pipeline.run(input_ref)` using OpenCV/PaddleOCR/Polars.
2. `CacheClient.publish("ocr.job.{id}", progress)`.
3. Persist artifacts; notify Java inbound API for ACID business writes.

## 6. Module plugin contract (Python)

```
modules/<feature>/python/
  plugin.py
  routers/
  workers/
  pipelines/
```

`plugin.register(app, registry)`:

- Include `APIRouter(prefix="/api/v1/ai/<feature>")`.
- `registry.worker("ocr.extract", handler)`.

If the folder is missing, FastAPI has no such router; workers have no such consumer.

## 7. Pipelines (heavy work)

| Pipeline | Module | Libraries (v1 intent) |
| -------- | ------ | --------------------- |
| Denoise / deskew / crop | `image-processing` | OpenCV |
| OCR extract | `ocr` | Open-source PaddleOCR or Tesseract; **high** vs **medium** via `python.vision.quality` (§7.2); inputs image/PDF/doc/HTML (§7.1) |
| Face verify / enroll | `identity` | Open-source ArcFace-class + PAD; quality §7.2 |
| Fingerprint template match | `identity` | **SourceAFIS** (Apache-2.0); quality §7.2 |
| Tabular parse | `ocr` | Polars |
| Thumbnail | `image-processing` | OpenCV |
| Mail MIME build / parse | `mail` | Kernel `opzhub_kernel.mail` ([23](23-mail-send-receive.md)) |
| Mail SMTP send | `mail` worker | `SmtpMailServer` |
| Mail IMAP receive | `mail` worker | `ImapMailServer` poll / IDLE |

Pipelines expose a pure function interface:

```
Pipeline.run(ctx: PipelineContext) -> PipelineResult
```

`PipelineContext` includes `DataClient`, `CacheClient`, cancellation token, and blob refs. Pipelines must be **idempotent** given the same `job_id`.

### 7.1 OCR inputs (doc, PDF, image, HTML)

The same `ocr.extract` job and models cover **four families**. Workers **normalize** to pages (raster + optional native text), then run the OCR engine. Feature UI does not pick a different API per type.

| Family | MIME / extensions (allow-list) | Normalize |
| ------ | ------------------------------ | --------- |
| **Image** | `image/jpeg`, `image/png`, `image/tiff`, `image/webp`, `image/bmp` | Decode (OpenCV); cap megapixels |
| **PDF** | `application/pdf` | Rasterize page-by-page (pypdfium2 / Poppler, OSI). If the page has a text layer, keep it and still OCR when `ocr.pdf.force_ocr` or the layer is empty/garbage |
| **Doc** | `application/msword` (`.doc`), `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (`.docx`), optional `.odt` / `.rtf` | Convert to PDF (LibreOffice headless in the **ocr** worker image), then the PDF path. `.docx` text layer extracted when present |
| **HTML** | `text/html`, `application/xhtml+xml` (`.html` / `.htm`) | Sanitize (no script/iframe/object). Extract visible text. OCR **embedded** images. Optional `ocr.html.render: true` renders to PDF (headless) then PDF path — **no** network: block `http(s):`, `file:`, `@import` URLs (SSRF) |

```
upload (mime) → ocr.ingest → pages[] { raster?, native_text? }
                         → ocr.extract (Paddle/Tesseract on rasters)
                         → ocr.parse (Polars / layout)
                         → unified result { kind, pages[], text, tables[] }
```

`modules.ocr.inputs` lists enabled families (default **all four** when the OCR module is packed). Disable a family in YAML; do not ship a second OCR app.

| Bound | YAML |
| ----- | ---- |
| Pages | `max_pages` (PDF/doc/HTML render) |
| Image | `image-processing.max_megapixels` |
| HTML | `max_html_kb`; reject if sanitize leaves nothing usable |
| Office / PDF | `max_upload_mb` |

Must not: execute JavaScript; fetch remote assets from HTML; run Office macros; load all PDF pages into RAM at once (one page or a small window — [14](14-performance-memory.md)).

`ocr.engine` is the **family** (`paddle` default for high; Tesseract allowed as medium fallback). Actual **quality** (high vs medium) is `python.vision.quality` (§7.2). Models: `ocr-models`; `opzhubctl` `ocr pull-models` pins **both** high and medium hashes; the process loads **one** set.

### 7.2 Open-source algorithms and quality (OCR, face, fingerprint)

All Python vision work uses **open-source** engines and pinned models ([13](13-open-source-licensing.md)). No closed cloud OCR/face/fingerprint API in the default pack.

**Intent:** run the **strong / high-accuracy** stack. Use **medium** only when the host cannot hold high (RAM/CPU), or the operator pins `medium`.

```yaml
python:
  vision:
    quality: auto                 # auto | high | medium
    auto:
      high_min_ram_mb: 4096       # cgroup / Compose mem_limit
      high_min_cpus: 2
      prefer_gpu: true            # if an OSI-usable GPU is present
```

| `quality` | When |
| --------- | ---- |
| `high` | Operator forces strong models (doctor warns if RAM is below `high_min_ram_mb`) |
| `medium` | Operator forces lighter models (busy small appliance) |
| `auto` | **At process start** for both child processes in `opzhub-be-core`: if RAM ≥ threshold and CPUs ≥ threshold → `high`, else `medium`. Log once. `/api/v1/ai/health` returns `vision.quality`. |

Do **not** switch quality mid-job or mid-login. Face challenge JSON uses the **resolved** id (`high` or `medium`) so web/Flutter PAD matches Python. Audit stores that id. Do not silently loosen FAR on a slow **phone**; only the **server** `auto` (or YAML) picks quality.

Load **one** weight set per process (high **or** medium), not both, unless RAM is explicitly sized for both (not the default).

#### Open-source stack (v1, pin URL + hash)

| Workload | High (stronger, more CPU/RAM) | Medium (lighter) | License bar |
| -------- | ----------------------------- | ---------------- | ----------- |
| OCR detect + recognize | PaddleOCR PP-OCRv4/v5 **server** det+rec | Paddle **mobile** det+rec **or** Tesseract LSTM | Apache-2.0 |
| OCR layout / tables | Paddle structure (Apache build) when packed | Line boxes + Polars only | Apache-2.0 |
| PDF raster | pypdfium2 / Poppler | same | BSD / GPL-2+CPE isolated in worker image only if Poppler; prefer pypdfium2 |
| Deskew / denoise | OpenCV | OpenCV, fewer passes | Apache-2.0 |
| Face detect | SCRFD ONNX | YuNet (OpenCV zoo) | Apache-2.0 / as pinned |
| Face embed | ArcFace-class **512-d** (InsightFace buffalo_l or equiv.) | MobileFaceNet / buffalo_s **128–256-d** | OSI or OpenRAIL; pin NOTICE |
| Face PAD | MiniFASNet-class + challenge/`proof` | Lighter PAD ONNX; **acts still required** | OSI or OpenRAIL |
| Fingerprint | **SourceAFIS** (Apache-2.0), 500 dpi, strict quality | SourceAFIS faster settings; still reject blank/wet | Apache-2.0 |

Forbidden as default: ABBYY, Google Vision, AWS Rekognition, Face++, closed AFIS, unpinned GitHub weights.

`opzhubctl doctor license` fails if a model file has no OSI/OpenRAIL record in `models.sha256`.

## 8. Isolation and safety

- Worker containers have CPU/memory limits in Compose (doc 09).
- File types allow-listed (`modules.ocr.inputs`: PDF, images, Office doc, HTML — §7.1). Unknown MIME → 415, no worker.
- Max pages / max pixels in module YAML.
- No pipeline imports `fastapi` (testable offline).
- Secrets only via env; never in image labels.

## 9. Data and cache usage

Python **may** read/write operational tables (`ai_job`, `ai_artifact`) through `DataClient` if those tables live in a kernel or `documents` schema.

Python **must not** update `ledger_*` or `inventory_*` tables even if credentials allow. Database roles: `erp_app` (Java) vs `ai_app` (Python) with distinct GRANTs. Design this in migrations:

- `ai_app`: DML on `ai_*` only.
- `erp_app`: DML on business schemas; may read `ai_*` for status.

## 10. Settings (Python)

`opzhub_kernel.settings` reads the same `platform.yaml` as Java. Feature flags:

```yaml
python:
  vision:
    quality: auto                 # auto | high | medium  (§7.2)
    auto:
      high_min_ram_mb: 4096
      high_min_cpus: 2
      prefer_gpu: true

modules:
  ocr:
    engine: paddle                 # paddle | tesseract (medium fallback)
    max_pages: 50
    max_upload_mb: 25
    max_html_kb: 2048
    inputs: [image, pdf, doc, html]
    pdf:
      force_ocr: false
    html:
      render: false
    doc:
      via: libreoffice
  image-processing:
    max_megapixels: 40
```

Unknown module keys are ignored if the module folder is absent.

## 11. Testing

- Router tests with `cache.type: memory`, `db.type: memory` (or sqlite test server if added).
- Pipeline tests with fixture **images, PDF, docx, and HTML**; no broker required.
- Worker integration: Testcontainers for Postgres, Valkey (Streams + Pub/Sub). No Kafka testcontainer in this drop.

## 12. Optional WebSocket on AI engine

Not required. Default: workers publish to Valkey; Java `CachePubSubBridge` pushes to the browser. Avoid two WS stacks unless a solution needs binary progress frames; if so, Nginx location `/ws/ai` is specified in doc 09 as optional.

## 13. Identity biometrics (login-class, healthy algorithms)

These run in **`opzpy` (opzhub-be-core)**. Login cannot wait on the OCR queue. Public clients never call these routes; Java calls them over **internal mTLS**.

```
POST /api/v1/ai/identity/bio/face/enroll
POST /api/v1/ai/identity/bio/face/verify
POST /api/v1/ai/identity/bio/fp/enroll
POST /api/v1/ai/identity/bio/fp/verify
```

Code: `modules/identity/python/pipelines/` (not `ocr/`). Kernel FastAPI mounts them only if `security.auth.methods` includes `face` and/or `fingerprint` with `template`.

### 13.1 Face (verify = 1:1)

Order is fixed. Skip a step → reject (`ok: false`). Do not return scores to Java beyond `ok` + coarse `why` enum (`quality` | `spoof` | `replay` | `mismatch` | `ok`).

Client already ran live PAD ([18](18-identity-rbac-abac-oauth2.md) §2.3). **Python repeats PAD** on the uploaded frames + `proof`.

Resolved quality is `python.vision.quality` (§7.2): `auto` → `high` or `medium` at process start. `security.auth.face.profile` may pin `high`/`medium`; `auto` follows vision quality. Challenge JSON carries the **resolved** id.

| Step | Healthy v1 (open source) | Not acceptable |
| ---- | ------------------------ | -------------- |
| Size / MIME | JPEG frames, YAML `max_frames_kb` | Gallery photo; raw video file |
| Detect | SCRFD (`high`) / YuNet (`medium`) | Multi-face pick-anyone |
| Quality | Blur, pose, min IPD | Always embed |
| PAD | MiniFASNet-class print+replay + `proof.acts` | No PAD; smile-only; trust client `ok`; closed SaaS |
| Align | 5-point similarity transform | Unaligned crop |
| Embed | ArcFace-class 512-d (`high`) or 128–256-d (`medium`), pinned hash | Pixel MSE; unpinned weights |
| Match | Cosine vs **that user**; FAR `1e-4` high / `1e-3` medium | Hard-coded `0.5`; 1:N |
| Models | `identity-models/{high,medium}/` | Training on customer faces in v1 |

Load one set per process. Inference-only. Timeout ~1500 ms high / ~600 ms medium. `OMP_NUM_THREADS=1`. Release `Mat` / ONNX in `finally`.

### 13.2 Fingerprint (template mode)

**SourceAFIS** only in Python (Apache-2.0). Same `python.vision.quality` for effort, not a second vendor AFIS.

| Quality | `high` | `medium` |
| --- | --- | --- |
| Input | Scanner template or 500-dpi PNG/WSQ | Same; skip extra enhance passes |
| Quality gate | Stricter SourceAFIS / NFIQ-class floor | Floor still rejects blank/wet |
| Extract / match | SourceAFIS 1:1, tighter YAML threshold | SourceAFIS 1:1, calibrated medium threshold |

PAD: sensor liveness bit when present. Java stores ciphertext of the **template only**. `fingerprint.mode: platform` (WebAuthn) never reaches Python.

### 13.3 Isolation from OCR

- Do not load PaddleOCR into the API process because face is enabled.
- Bio ONNX and Paddle execute in `opzhub-be-core`.
- `opzhubctl start all` starts `opzpy` when an AI/OCR/mail module or face/fingerprint-template login is present; it runs both the API and worker.
- `modules/mail` starts `opzpy` for both send-queue and IMAP polling. No Paddle. Routes under `/api/v1/ai/mail/` are **Java mTLS only** ([23](23-mail-send-receive.md)).
