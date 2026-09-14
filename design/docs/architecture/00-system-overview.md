# 00 — System Overview

## 1. Purpose

This platform is a **plug-and-play application suite** (short name **opzhub**). The vendor catalog is **50+ applications**. Each customer receives the kernel plus **only the applications they bought** (HR, or ticketing, or both, or any other subset) — never the full catalog.

Typical workloads those applications cover:

- HR, ticketing, finance, inventory, and other line-of-business apps (one folder each).
- High-integrity financial operations when `ledger` is in the pack (ACID, multi-currency).
- Heavy media work when OCR/image applications are in the pack. Python vision (OCR, face, fingerprint) is **open source**, **high** accuracy by default, **medium** when the host is small (`python.vision.quality: auto`).
- Real-time UI (WebSockets) without blocking the public gateway.

The same kernel (`common/`) is reused across every customer. Differentiation is **which application folders exist in that customer’s pack**, not forked cores. Packaging rules: [01](01-plugin-play-model.md), [10](10-solution-composition.md).

## 2. Architectural principles

| Principle | Rule |
| --------- | ---- |
| Kernel vs module | `common/` is never customer-specific. Each **application** is `modules/<id>/` and is omitted from packs that did not buy it. |
| Folder is the plugin | Presence of the application directory is the source of truth. Do not ship 50+ apps and hide them with flags. |
| Deliver the subset | Customer tarball = kernel + shared modules needed + **listed applications only**. |
| Contract over implementation | Frontend fields, backend services, scripts, DB, and cache talk to **interfaces**. Physical engines are selected by YAML. |
| Split runtimes by workload | Relational ACID work stays in Java/Spring. CPU/GPU vision and parsing stay in Python workers. |
| One public door | Nginx is the only internet-facing process (80/443). All other services bind to an internal Docker network. |
| Fail closed | A missing module must not crash boot. Routes, menus, workers, and migrations for that module simply do not register. |
| Swap storage without rewrite | Application code uses `DataClient`, `CacheClient`, and `BrokerClient`. Engines are selected by YAML (`db.type`, `cache.type`, `broker.type`). |
| Encrypt every hop | Public and internal traffic use TLS. Prod forbids plaintext HTTP, WS, Postgres, Valkey, and broker protocols. |
| Open source | Kernel, modules, and default stack are OSI-licensed (Apache-2.0 project). No proprietary runtime in default packs. |
| Production efficiency | Bounded pools/queues; explicit close of native buffers; no unbounded caches ([14](14-performance-memory.md)). |
| Non-default app ports | Internal HTTPS uses 8114 / 8117 / 8109, not 8080 / 8000 / 8443. Nginx listens HTTPS directly on 8102 (mapped to host 443 in prod) — no HTTP listener, no port-forwarding translation. |
| Non-root processes | App runs as **tsuser**. Release is `/home/tsuser/opzhub` (replaced on upgrade). Site/state is `/etc/opzhub` + `/var/lib/opzhub` + `/var/log/opzhub` (one-time, kept). |
| Production-first | Ship shape is Compose on EC2. **Dev is the same code** with `profile: dev` + a Compose overlay ([17](17-dev-prod-implementation.md)). |
| Dual deploy | **Site** (FE+BE+DB on the customer box) or **central** hub (same FE/BE). Central has two data subtypes: Postgres on the hub, or **DB only** at the customer (BE `DataRouter`). Typing `acme/user` **live-checks the license server**; password type is entitled there before login ([20](20-licensing-site-central.md)). |
| Dual clients | Web (React) and mobile (**Flutter**, Android + iOS) share field keys, APIs, and module folders. Face login is live camera + PAD on **both**. Chrome is **`gui.mode: lite`** or **`rich`**. |

## 3. Logical topology

```
                         ┌─────────────────────────┐
                         │   Clients (Browser SPA + Flutter) │
                         └────────────┬────────────┘
                                      │  HTTPS / WSS :443
                         ┌────────────▼────────────┐
                         │  Nginx API Gateway      │
                         │  TLS, path routing, WSS │
                         └───────┬─────────┬───────┘
                                 │         │  HTTPS / WSS (internal CA)
              /api/v1/opzhub/*   │         │      /api/v1/ai/*
              /ws/opzhub         │         │      /ws/ai  (optional)
                                 │         │
                    ┌────────────▼──┐   ┌──▼────────────┐
                    │ Spring Boot   │   │ FastAPI       │
                    │ Core ERP      │   │ Media & OCR   │
                    │ (virtual      │   │ (async routes │
                    │  threads)     │   │  + workers)   │
                    └─┬─────┬─────┬─┘   └──┬─────┬──────┘
                      │     │     │        │     │
           ┌──────────┘     │     └────────┘     │
           │ TLS            │ TLS          TLS   │
    ┌──────▼──────┐  ┌──────▼──────┐      ┌──────▼──────┐
    │ DataClient  │  │ CacheClient │      │ BrokerClient│
    │ → Postgres  │  │ → Valkey    │      │ → Valkey    │
    │   Server    │  │   Pub/Sub   │      │   Streams   │
    └─────────────┘  └─────────────┘      └──────┬──────┘
                                                 │
                                          ┌──────▼──────┐
                                          │ Python      │
                                          │ OCR/Image   │
                                          │ workers     │
                                          └─────────────┘
```

Internal Docker bridge network: `opzhub-internal`. No Spring, FastAPI, PostgreSQL, Valkey, or broker port is published except through Nginx (and optional SSH/ops tunnels). All internal clients use TLS (see [12 — Transport Security](12-transport-security-tls.md)). **Release** files live under `/home/tsuser/opzhub` (replaced on upgrade). **Site and state** live under `/etc/opzhub`, `/var/lib/opzhub`, and `/var/log/opzhub` (one-time, kept).

## 4. Tech stack (locked for v1, abstract where it matters)

| Layer | v1 choice | Why | Swap path |
| ----- | --------- | --- | --------- |
| Public proxy | Nginx | Path routing + WebSocket upgrade on one port | Config contract in doc 09 |
| ERP engine | Java 21+, Spring Boot 3.x, virtual threads | ACID, structured concurrency for validations | Module SPI stays |
| AI/OCR engine | Python 3.12+, FastAPI | Async IO; OpenCV / Polars / PaddleOCR or Tesseract | Worker contract stays |
| Relational store | PostgreSQL 16 | Integrity, indexing, constraints | `db.type` in YAML |
| Cache / WS backplane | Valkey | Session + Pub/Sub | `cache.type` in YAML |
| Async broker | Valkey Streams (reuse Valkey) | Durable OCR/image/mail jobs | Kafka optional **later** ([11](11-broker-selection.md)) — **not this drop** |
| Transport | TLS 1.2+ / HTTPS / WSS / mTLS | Every hop, including stores | [12](12-transport-security-tls.md) |
| Frontend (web) | React + TypeScript + Vite | Shared field kernel; forms + RBAC apply have **no** app names | Kernel `FieldDef` / FormEnvelope ([22](22-common-fields-forms-fk.md)) |
| Frontend (mobile) | **Flutter** (Android + iOS) | Same plugin-play and field keys as web | [03](03-frontend-design.md) §14 |
| Orchestration | Docker Compose | Single EC2, volume persistence | Same service names |
| Host | Ubuntu 24.04 LTS on AWS EC2 | Documented bootstrap | Doc 09 |

## 5. Workload split (non-negotiable)

### 5.1 Must stay on the ERP engine (Java)

- Multi-currency ledgers, journals, posting, period close.
- Inventory on-hand, reservations, cost layers.
- Any write that requires a single ACID transaction across multiple tables.
- AuthN/AuthZ for business APIs, audit trails, tenancy. Java **issues the session** for password, OAuth2, face, and fingerprint; Python only returns match ok/fail.

### 5.2 Must stay on the Media engine (Python) and must not block the gateway

- Document OCR for **images, PDF, Office doc, and HTML** (normalize then extract).
- Image cleaning, deskewing, denoising, thumbnail generation.
- Long-running extraction that publishes progress over the cache Pub/Sub bus.
- **Face and fingerprint matching** for login: client live-PAD (reject photo/replay); Python match + PAD on `opzpy` with `medium` or `high` profile — not the OCR queue ([05](05-backend-python-design.md) §13, [18](18-identity-rbac-abac-oauth2.md)).
- **Mail send and receive** (SMTP/IMAP/MIME) on Python; Java only queues metadata and calls Python ([23](23-mail-send-receive.md)).

Synchronous FastAPI routes **accept metadata and enqueue work** for OCR/image. They do not run Paddle/deskew on the request thread. Face/fingerprint verify is the documented **login-class** exception (bounded ONNX, hard timeout).

### 5.3 Real-time path

1. Client opens WebSocket through Nginx (`Upgrade` headers).
2. Spring (and optionally FastAPI) holds the socket.
3. Valkey Pub/Sub is the **horizontal coordination backplane** so multiple app instances can fan-out notifications.
4. Python workers publish job progress (`ocr.job.<id>`, `notify.user.<id>`) into Valkey; Java subscribers push to connected sockets.

## 6. Quality attributes

| Attribute | Target |
| --------- | ------ |
| Integrity | Ledger and inventory writes are transactional; isolation level documented per use case (default READ COMMITTED; serializable for selected posting). |
| Isolation of heavy work | OCR/image never shares the Spring request pool. Native OpenCV/Paddle stay in recycled worker processes ([14](14-performance-memory.md)). |
| Observability | Structured logs with `solution_id`, `module_id`, `correlation_id`; health on `/healthz` per service. User errors: safe `msg` + copyable id ([24](24-hang-prevention-error-reporting.md)). |
| Hang safety | Every I/O has a deadline; jobs `flock` + kill-after; no leftover pool/lock ([24](24-hang-prevention-error-reporting.md), [16](16-jobs-crontab.md)). |
| Operability | Compose `up -d`; volumes for Postgres and Valkey; one Nginx port. |
| Packagability | Customer pack has **only** bought applications. HR-only has no ticketing folder, routes, or tables. |
| Replaceability | Changing `db.type`, `cache.type`, or `broker.type` does not change controllers, pages, or scripts. |
| Confidentiality | Prod traffic is TLS end-to-end; plaintext protocols fail `opzhubctl doctor`. |
| License | Apache-2.0 project; dependency allow-list ([13](13-open-source-licensing.md)). |
| Memory / speed | Steady RSS under soak; OCR isolated; leak detectors in CI ([14](14-performance-memory.md)). |

## 7. Bounded contexts (feature modules)

These map 1:1 to removable folders. The vendor catalog is **50+**; the table is a sample. A customer pack contains **only** rows they bought (plus `requires`). See [10](10-solution-composition.md).

> Folder column below predates the `common/` / `modules/` / `apps/` split now
> codified in [IMPLEMENTATION_RULES.md Rule 5](../requirement/IMPLEMENTATION_RULES.md#rule-5--directory-structure-common--modules--apps):
> shared, non-application features live under `modules/<id>`; sold,
> individually licensed applications live under `apps/<id>`. New rows added
> after that split (e.g. People below) state their folder explicitly.

| Context | Folder | In a pack when |
| ------- | ------ | -------------- |
| Identity & access | `identity` | Shared; usual with any UI app (RBAC/ABAC + OAuth2 + optional face/fingerprint, [18](18-identity-rbac-abac-oauth2.md)) |
| Admin / settings | `admin` | Shared; usual with any UI app |
| Human resources | `hr` | Sold (customer 1 or 3) — generic placeholder. Concrete build is `apps/manage-my-hr` ([33](33-hr-wrapper-design.md)), a presentation-only wrapper with **zero schema of its own**: attendance and leave (the category-agnostic core) live in `apps/manage-my-people` ([27](27-people-domain-design.md)) instead, since they apply equally to `manage-my-students`. Goals/OKRs, recognition/rewards, recruitment/ATS, and workplace-only elaborations (geofenced check-in/out, payroll rollups, comp-off) remain real, evidenced, and reserved for `apps/manage-my-hr` once one of them gets its own future design phase. |
| People (generic person master, incl. attendance & leave core) | `apps/manage-my-people` | Sold; foundation for any person-tracking app, not just HR. Category-agnostic attendance/leave live here because they apply to employees and students alike ([27](27-people-domain-design.md)). Wrapper apps (`apps/manage-my-hr`, `manage-my-hra`, `manage-my-students`) reference it, they do not fork it. |
| Business data directory | `apps/manage-my-data` | Sold (`opz-001`). Centralized business/lead record management: intake, assignment, verification, publish workflow, plus public inquiries/reviews ([29](29-data-directory-domain-design.md)). Distinct from `master-data` below, which is shared reference data pulled in by other apps' `requires`, not a sold, standalone product. |
| Ticketing / ITSM | `ticketing` | Sold (customer 2 or 3) — generic placeholder; concrete build is `apps/manage-my-desk` (paid service tickets with a commercial-clearance gate, SLA clock, activity monitoring — [32](32-desk-domain-design.md)). `manage-my-ticket` (separate catalog entry) is a distinct, narrower future app. |
| Marketing / lead generation | `apps/manage-my-market` | Sold (`opz-004`, renamed from `manage-my-marketing`). Lead/CRM, campaign & journey automation, telemarketing, referral marketing ([30](30-market-domain-design.md)). SEO monitoring and social media management are real but deferred to a future phase — see doc 30 §1. |
| Ledger / finance | `ledger` | Sold — generic placeholder for general-ledger-grade functionality (double-entry journal posting, revenue recognition). Concrete near-term build is `apps/manage-my-finance` (customers, invoices, payments, receivables — AR/billing, not a GL — [31](31-finance-domain-design.md)), same "concrete app vs. generic placeholder" split as `apps/manage-my-desk`/`ticketing` and `apps/manage-my-data`/`master-data`. |
| Inventory | `inventory` | Sold |
| Master data | `master-data` | Pulled in by finance/stock `requires` |
| Notifications | `notifications` | Pulled in when an app needs WS |
| Documents | `documents` | Pulled in by OCR / attachments |
| OCR | `ocr` | Sold |
| Image processing | `image-processing` | Sold |
| Reporting | `reporting` | Optional sold / shared |

`common/` is **not** a bounded context. It is the kernel every context depends on.

## 8. Communication styles

| From → To | Style | Notes |
| --------- | ----- | ----- |
| Browser or Flutter → Nginx → Spring | HTTPS REST `/api/v1/opzhub/*` | JSON, idempotency keys on money writes |
| Browser or Flutter → Nginx → FastAPI | HTTPS REST `/api/v1/ai/*` | Accept job, return `job_id` |
| Browser or Flutter → Nginx → Spring WS | **WSS** `/ws/opzhub` | Notifications, job progress relay |
| Nginx → apps | HTTPS / WSS + mTLS | Internal CA |
| Spring → Data server | Client API over Postgres TLS | Never raw JDBC in feature modules |
| Spring / FastAPI / workers → Cache server | Client API over Valkey TLS | Sessions, Pub/Sub, short TTL state |
| Spring → Broker | `BrokerClient` over TLS | Valkey Streams (v1). Kafka later, not implemented |
| Workers → Cache | Pub/Sub events over TLS | Progress and completion |
| Workers → ERP inbound | HTTPS mTLS | Persist extraction results via Java for ACID writes |
| Spring → FastAPI (internal) | HTTPS mTLS `/api/v1/ai/identity/bio/*` | Face/fingerprint verify; not on public Nginx |

**Recommended write policy for extracted ERP data:** Python workers publish results; Java inbound service validates and commits to the ledger/inventory schema. That keeps ACID ownership in one engine.

## 9. Security baseline (design)

- **TLS on every hop** (public HTTPS/WSS and internal HTTPS, Postgres, Valkey). Nginx is the public terminator; apps and stores still use an internal CA. Details: [12 — Transport Security](12-transport-security-tls.md). Kafka TLS applies only if Kafka is added in a later drop.
- JWT or session token in HTTP-only **Secure** cookie (web) or Bearer (Flutter). Login methods: **password, OAuth2, face, fingerprint** (subset in YAML). Access at login is compact: **app → feature → letters** (`"emp":"vcu"`). RBAC+ABAC: [18](18-identity-rbac-abac-oauth2.md).
- Module APIs are unauthorized unless `identity` is present; if `identity` is removed, only an explicit solution flag `security.allow_open_dev` may relax this (forbidden in production YAML profiles).
- Object storage for images is out of v1 default (local volume). Design a `StorageClient` the same way as DB/cache when S3 is introduced (S3 also uses TLS).

## 10. What “reusable for all solutions” means

- **Common code** implements fields (web + Flutter), grids, auth shell, HTTP/WS clients, DataClient, CacheClient, CLI, logging, error model. Checkbox, radio, free text, single/multi dropdown, **import/export/bulk CUD**, and **generic** RBAC apply live only here. Mandatory/optional/rules and `io`/`bulk` flags arrive from Java with the form ([22](22-common-fields-forms-fk.md)).
- **Modules** implement domain screens, domain APIs, domain workers, domain migrations.
- **Solutions** are manifests + env + which module folders are on disk.

**Testing** is not a sold module and not this product tree. Unit / functional / performance live in a separate QA application: [21](21-testing-application.md).

No customer-specific business rule belongs in `common/`.
