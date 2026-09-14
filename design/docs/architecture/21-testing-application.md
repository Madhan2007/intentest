# 21 — Testing application (separate repository tree)

This document is **design only**. Do not add pytest, Locust, or JUnit files in this drop.

The **product** repository follows [02](02-repository-folder-structure.md): `common/`, `modules/`, `solutions/`. The **testing application** is a **different layout** (recommended: a **second git repo**, e.g. `opzhub-qa`). It is grouped by **kind of test**, not by sold application folders.

Customer packs and `/home/tsuser/opzhub` **do not** ship this tree.

## 1. Why not reuse [02](02-repository-folder-structure.md)

| Product | Testing application |
| ------- | ------------------- |
| Kernel vs `modules/<id>` | Top-level **unit**, **functional**, **performance** |
| Feature code next to `db/` / `frontend/` | Suites talk to the **public origin** or isolated fakes |
| `opzhubctl` process names | QA runners: unit / functional / performance |
| Plugin-play omit unused apps | Suites are **selected by pack** (`suites.yaml` lists enabled apps), not by copying `modules/hr` |

Tiny in-module tests (`memory` DataServer) may exist later inside the product. They are not the QA system of record.

## 2. Designed folder structure

```
opzhub-qa/                         # separate repo (or sibling dir later — not now)
├── README.md
├── pyproject.toml                 # pytest + locust (implementation later)
├── qa.env.example                 # OPZHUB_ORIGIN; no secrets in git
├── bin/
│   ├── run-unit.sh
│   ├── run-functional.sh
│   └── run-performance.sh
├── support/                       # QA helpers only — not DataClient, not modules/
│   ├── clients/                   # HTTPS to public origin ([12](12-transport-security-tls.md))
│   ├── fixtures/
│   └── data/                      # sample PDF/HTML/PNG for OCR functional (later)
├── 01-unit/                       # (1) unit
│   ├── python/                    # pytest vs helpers + kernel memory servers
│   ├── java/                      # JUnit 5 vs DataClient memory
│   ├── web/                       # optional vitest for field kit
│   └── mobile/                    # optional Dart unit (no device)
├── 02-functional/                 # (2) functional
│   ├── api/                       # REST/WSS through Nginx
│   ├── web/                       # optional Playwright against SPA
│   ├── mobile/                    # optional; public origin only
│   └── suites.yaml                # which checks for which pack
├── 03-performance/                # (3) performance
│   ├── locustfile.py              # or k6 scripts
│   ├── scenarios/                 # login, OCR job, ledger post mix
│   └── thresholds.yaml            # p95 / error rate gates ([14](14-performance-memory.md))
└── reports/                       # generated; gitignored
```

No `common/`, `modules/`, or `solutions/` in this repo.

## 3. Unit tests

**Goal:** fast, no Compose, no camera, no OCR GPU.

| Area | Assert |
| ---- | ------ |
| Login parse | `acme/ada`, `ada@acme` ([20](20-licensing-site-central.md)) |
| License payload | resolve JSON must not contain `db_host` / secrets |
| Access matrix | letters `vcua` only; `has(app,feature,letter)` ([18](18-identity-rbac-abac-oauth2.md)) |
| Form apply | Envelope `req`/`mode`/`rules`; kernel must not branch on `"hr"` ([22](22-common-fields-forms-fk.md)) |
| Collection IO | Toolbar hides import/bulk when envelope `io`/`bulk` is false; export does not embed secrets |
| Mail | MIME parse rejects script/HTML fetch; Java has no SMTP in feature tests ([23](23-mail-send-receive.md)) |
| Hang / errors | Envelope always has `correlation_id`; timeout maps to `code: timeout`; prod body has no stack ([24](24-hang-prevention-error-reporting.md)) |
| FK | Real `REFERENCES` for required parents; logical FK when the parent app is optional |
| OCR MIME | `image` / `pdf` / `doc` / `html` allow-list ([05](05-backend-python-design.md) §7.1) |
| Vision quality | `python.vision.quality` auto → high vs medium from RAM/CPU ([05](05-backend-python-design.md) §7.2) |
| GUI skin | `lite` vs `rich` ids only ([03](03-frontend-design.md) §2.1) |
| Kernel clients | `db.type: memory` / `cache.type: memory` ([07](07-data-cache-client-server.md)) when product code exists |

Do **not** unit-test live face PAD with a JPEG file (forbidden capture). Mock `proof` + frames at the Python pipeline boundary.

## 4. Functional tests

**Goal:** one running pack (site or hub). Client uses `OPZHUB_ORIGIN` only — never Postgres, Valkey, or `:8114`/`:8117`.

| Flow | Notes |
| ---- | ----- |
| Health | `/healthz` or `/api/v1/opzhub/health` |
| Meta auth | tiny `methods` list; after resolve, UI uses license `auth[]` |
| License live | `POST …/license/resolve` `{ live: true }` while typing; `st`, `auth`, optional `skin` |
| Password type | field only if `auth` contains `password`; submit still `phase: login` on license server — tests must **not** send the password to `license.hub_url` |
| Session matrix | compact `a` object; no `"view"` words |
| OCR intake | allow listed MIME; `application/zip` → 415; kinds image/pdf/doc/html |
| Face login API | reject gallery/file body; live camera is **manual** in v1 |
| Pack subset | HR-only origin never exposes ticketing routes |

Flutter: same API cases with Bearer; no second backend.

Skip or mark `not_implemented` until runtimes exist ([17](17-dev-prod-implementation.md)).

## 5. Performance tests

**Goal:** bounded load; fail CI if thresholds miss. Align with [14](14-performance-memory.md).

| Scenario | Mix (illustrative) |
| -------- | ------------------ |
| Public | health + license live resolve |
| ERP | authenticated reads; optional money POST with `Idempotency-Key` |
| OCR | enqueue jobs (not full Paddle on every virtual user unless the overlay sizes workers) |
| Bio | do **not** flood face verify; login-class pool is small |

`thresholds.yaml` (design): p95 for health and resolve; max error rate (e.g. 1%); max users; soak optional and explicit. Unbounded Locust is not a gate.

Tooling intent (OSI): **Locust** (MIT) and/or **k6** (AGPL — isolate from kernel, [13](13-open-source-licensing.md)); default Locust.

## 6. Config (later)

```yaml
# suites.yaml (design)
origin: ${OPZHUB_ORIGIN}
pack: hr-only                    # or hub
skip_unimplemented: true
unit: [parse, matrix, ocr_mime, vision_quality]
functional: [health, license_live, ocr_mime]
performance:
  users: 10
  spawn: 2
  run_seconds: 60
```

## 7. Implementation later (not this drop)

1. Create repo `opzhub-qa` with the tree in §2.
2. Add pytest/JUnit/Locust behind `bin/run-*.sh`.
3. CI: unit always; functional + performance only when a pack is up.

## 8. What must not happen

- Copying this tree into `modules/` or into a customer tarball as a sold app.
- Importing `modules/hr/...` from QA (hardcoded product paths).
- Sending passwords or bio images to the **license** server.
- Performance jobs without `thresholds.yaml`.
- Automating face login by uploading a photo or gallery still ([18](18-identity-rbac-abac-oauth2.md)).
- Adding test files under the product repo in this design drop.
