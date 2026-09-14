# 08 — Configuration YAML

All runtimes load the **same files**. Later implementations bind these schemas; this document is the contract.

## 1. File merge order (lowest → highest)

1. `platform/config/platform.yaml` — defaults, types, capabilities (in the **release**)
2. `solutions/<id>/config/platform.override.yaml` — customer pack
3. `/etc/opzhub/platform.override.yaml` — **site**, kept across upgrades (prod/staging host)
4. Environment variables (`OPZHUB_*`, `POSTGRES_*`, `VALKEY_*`)
5. CLI flags on `opzhubctl` only

Dev laptops typically have no `/etc/opzhub`; they stop at (2) plus env. Prod always has (3). See [17](17-dev-prod-implementation.md).

Later wins. Secrets never committed; `*.env.example` lists keys.

## 2. `platform.yaml` (canonical schema)

```yaml
schema_version: "2026.1"

solution:
  id: kernel-dev
  display_name: Kernel
  profile: dev                    # dev | staging | prod  (see doc 17)

deploy:
  mode: site                      # site | central  (doc 20)
  company: acme                   # required when mode=site; omit on hub

license:
  hub_url: https://license.example.com
  instance_id: ${OPZHUB_INSTANCE_ID}
  grace_days: 7
  lease_path: /etc/opzhub/license.lease
  live_ms: 400                    # login field debounce; 0 illegal in prod
  validate_on_login: true         # required in prod; license server before password
  validate_ttl_seconds: 45        # live cache; login re-validates if stale

clients:
  web: true                       # React SPA (opzgui)
  mobile: true                    # Flutter (omit store builds if false)

gui:
  mode: lite                      # lite | rich  (doc 03 §2.1)
  allow_user_choice: true         # header toggle; persist locally
  lite:
    icons: line                   # SVG / IconData only
    images: false
  rich:
    icons: pack                   # icon pack + optional raster
    images: true                  # branding/rich + module assets/rich
    max_inflight: 4               # concurrent image fetches
    motion: respect               # never ignore prefers-reduced-motion

kernel:
  api_version: "2026.1"

runtime:
  user: tsuser                    # only application operator (host + app containers)
  uid: 2100
  gid: 2100

security:
  allow_open_dev: false           # must be false when profile=prod
  session_ttl_seconds: 28800
  auth:
    methods: [password, oauth2, face, fingerprint]   # at least one
    password:
      enabled: true
      lockout_attempts: 8
    face:
      enabled: true
      profile: auto                 # auto | medium | high (auto → python.vision.quality)
      far_target: 1.0e-4            # when resolved quality is high; medium uses 1.0e-3
      liveness: required            # required in prod
      client_pad: required          # live camera; reject photo/replay
      capture: live
      min_frames: 3
      max_image_kb: 400
      max_frames_kb: 1200
    fingerprint:
      enabled: true
      mode: [platform, template]    # platform=WebAuthn; template=Python
      max_image_kb: 200
    oauth2:
      enabled: true
      jit: false
      providers:
        - id: company-sso
          type: oidc
          issuer: https://idp.example.com
          client_id: ${OAUTH_CLIENT_ID}
          client_secret: ${OAUTH_CLIENT_SECRET}
          scopes: [openid, profile, email]
          pkce: true
  access:
    matrix_format: letters        # letters | bits — "vcua" or int; never "view"
  forms:
    envelope_cache_seconds: 60    # FormEnvelope by form+ver+roles; 0 = no cache (doc 22)
    io_max_rows: 10000            # import/export/bulk cap (doc 22 §8)
    io_max_upload_mb: 8
  errors:
    debug: false                  # extra debug.* in JSON; illegal in prod (doc 24)
  tls:
    mode: required                # required in prod; see doc 12
    min_version: "1.2"
    allow_insecure_dev: false
    public:
      listen_https: 443           # HTTPS only — nginx no longer publishes a plaintext HTTP listener
      hsts_seconds: 31536000
      cert_path: /etc/opzhub/certs/public/fullchain.pem
      key_path: /etc/opzhub/certs/public/privkey.pem
    internal:
      ca_path: /etc/opzhub/certs/internal/ca.crt
      mtls: true
      core_url: https://opzhub-be-app:8114
      ai_url: https://opzhub-be-core:8117
      web_url: https://opzhub-web-app:8109

hang:
  http_ms: 15000                  # public API deadline (doc 24)
  java_python_ms: 8000
  db_checkout_ms: 3000
  db_statement_ms: 20000
  cache_ms: 500
  broker_ack_ms: 10000
  mail_smtp_ms: 8000
  mail_imap_poll_ms: 30000
  job_default_s: 300
  job_kill_after_s: 15
  ws_idle_s: 90
  lock_stale_s: 3600

db:
  type: postgres                  # postgres | memory
  router: off                     # off | company (hub: DSN per tenant; doc 20)
  required_capabilities:
    - acid_transactions
    - relational_constraints
  migrations:
    dialect: postgres
  postgres:
    host: postgres
    port: 5432
    database: erp
    user: erp_app
    password: ${POSTGRES_PASSWORD}
    ssl_mode: verify-full         # prod: verify-full; never disable when profile=prod
    pool_min: 4
    pool_max: 32
    ai_user: ai_app               # Python role; password ${POSTGRES_AI_PASSWORD}
  memory: {}                      # used when type=memory

cache:
  type: valkey                    # valkey | memory
  required_capabilities:
    - pubsub_horizontal
    - ttl_keys
  valkey:
    host: valkey
    port: 6380                    # TLS port; plaintext 6379 disabled in prod
    tls: true
    password: ${VALKEY_PASSWORD}
    db: 0
    key_prefix: erp
    pool_max: 32
  memory:
    max_keys: 10000

broker:
  type: valkey                    # valkey (v1) | memory (tests). kafka reserved — not implemented (doc 11)
  required_capabilities:
    - competing_consumers
    - ack
    - retry
  valkey:                         # used when broker.type=valkey — same host, isolated DB
    host: valkey
    port: 6380
    tls: true
    password: ${VALKEY_PASSWORD}
    db: 2                         # not 0 (cache) — Streams live here
    stream_maxlen: 100000
  # kafka:                        # OPTIONAL LATER — do not implement; doctor fails broker.type=kafka
  #   bootstrap: kafka:9093
  #   security_protocol: SASL_SSL
  #   tls_ca: /etc/opzhub/certs/internal/ca.crt
  #   topic_prefix: opzhub

http:
  opzhub-be-app:
    internal_url: https://opzhub-be-app:8114
    port: 8114                    # not 8080 / 8443
  opzhub-be-core:
    internal_url: https://opzhub-be-core:8117
    port: 8117                    # not 8000 / 8443
  opzhub-web-app:
    internal_url: https://opzhub-web-app:8109
    port: 8109
  opzhub-ui-service:
    container_https: 8102         # host 443 in prod; nginx no longer publishes a plaintext HTTP listener
  worker_health:
    port: 8124                    # optional; not public
  public:
    origin: https://erp.example.com

realtime:
  ws_path: /ws/opzhub
  heartbeat_seconds: 25
  nginx_read_timeout_seconds: 3600

python:
  vision:
    quality: auto                 # auto | high | medium  (doc 05 §7.2)
    auto:
      high_min_ram_mb: 4096
      high_min_cpus: 2
      prefer_gpu: true

modules:
  enabled: []                     # usually comes from solution.manifest; may overlay
  disabled: []
  ocr:
    engine: paddle                # paddle | tesseract
    max_pages: 50
    max_upload_mb: 25
    max_html_kb: 2048
    inputs: [image, pdf, doc, html]
    pdf:
      force_ocr: false
    html:
      render: false               # no outbound HTTP when rendering
    doc:
      via: libreoffice
  image-processing:
    max_megapixels: 40

mail:
  enabled: true                   # ignored if modules/mail absent (doc 23)
  send:
    type: smtp                    # smtp | memory
    sync_timeout_ms: 8000
    host: smtp.example.com
    port: 587
    tls: starttls                 # starttls | implicit
    user: ${MAIL_SMTP_USER}
    password: ${MAIL_SMTP_PASSWORD}
    from: noreply@example.com
  receive:
    type: imap                    # imap | memory | off
    host: imap.example.com
    port: 993
    tls: implicit
    user: ${MAIL_IMAP_USER}
    password: ${MAIL_IMAP_PASSWORD}
    mailbox: INBOX
    poll_seconds: 60
  limits:
    max_part_kb: 2048
    max_recipients: 50
    max_html_kb: 256

logging:
  level: INFO
  json: true

backup:
  enabled: true
  interval_cron: "0 * * * *"
  format: custom
  migrate_on_start: true          # create/update schema; never wipe rows
  local:
    enabled: true
    dir: /var/lib/opzhub/backup/db
    keep: 48
  ftp:
    enabled: false
    protocol: ftps                # ftps | sftp | ftp (ftp not in prod)
    host: ftp.example.com
    port: 21
    user: ${BACKUP_FTP_USER}
    password: ${BACKUP_FTP_PASSWORD}
    dir: /opzhub/${SOLUTION_ID}
    tls: true
    keep: 168
```

### 2.1 Flag control summary

| Flag | Controls |
| ---- | -------- |
| `db.type` | Which `DataServer` is constructed in Java, Python, scripts |
| `cache.type` | Which `CacheServer` is constructed |
| `broker.type` | Which `BrokerServer` is constructed (`valkey` = Streams on existing Valkey). **`kafka` is optional later and fails doctor in this drop** ([11](11-broker-selection.md)) |
| `security.tls.mode` | `required` forces HTTPS/WSS and TLS to Postgres/Valkey/broker |
| `modules.enabled` / disk folders | Which plugins register |
| `modules.<id>.*` | Feature settings; ignored if folder absent |
| `security.allow_open_dev` | Auth bypass; illegal in prod |
| `security.auth.methods` | `password`, `oauth2`, `face`, `fingerprint` (any non-empty subset) |
| `security.auth.face.profile` | `auto` \| `medium` \| `high`. Live camera + client PAD ([18](18-identity-rbac-abac-oauth2.md) §2.3) |
| `security.access.matrix_format` | Login matrix: `letters` (`vcua`) or `bits`; per **feature** inside each app |
| `security.forms.envelope_cache_seconds` | Cache FormEnvelope (`req`/`mode`/`rules`) per form+roles ([22](22-common-fields-forms-fk.md)) |
| `security.forms.io_max_rows` | Cap import/export/bulk rows; jobs above `bulk.max` |
| `security.forms.io_max_upload_mb` | Import file size before reject |
| `security.errors.debug` | Extra `debug` in API JSON; **false** in prod ([24](24-hang-prevention-error-reporting.md)) |
| `hang.*` | Deadlines for HTTP/DB/cache/broker/mail/jobs; 0 illegal in prod |
| `deploy.mode` | `site` (FE+BE+DB on one box) or `central` (shared hub FE/BE + company directory) |
| `db.router` | `off` on site; `company` on hub — pick hub vs customer Postgres per slug ([20](20-licensing-site-central.md)) |
| `license.hub_url` | Central license server; live resolve while typing; password **type** check before login |
| `gui.mode` | `lite` (light-weight) or `rich` (attractive: images + icon packs). Same FE code ([03](03-frontend-design.md) §2.1) |
| `python.vision.quality` | `auto` (RAM/CPU pick **high** or **medium**), or pin. Open-source OCR/face/fingerprint ([05](05-backend-python-design.md) §7.2) |
| `modules.ocr.inputs` | OCR families: `image`, `pdf`, `doc`, `html` ([05](05-backend-python-design.md) §7.1) |
| `mail.send.type` / `mail.receive.type` | SMTP send / IMAP receive in **Python only**; Java uses `MailPort` ([23](23-mail-send-receive.md)) |
| `backup.local` / `backup.ftp` | Hourly dump on box and/or FTPS/SFTP ([19](19-db-backup-migrate.md)) |
| `backup.migrate_on_start` | Apply pending schema versions before `opzbe` listens |

## 3. `solution.manifest.yaml`

```yaml
schema_version: "2026.1"
solution:
  id: acme-erp
  display_name: Acme Manufacturing
  kernel_min: "2026.1"

modules:
  enabled:
    - identity
    - admin
    - hr                      # sold apps only; do not list the 50+ catalog
  disabled: []

branding:
  theme_override: ./branding/theme.override.json
  rich_dir: ./branding/rich           # ignored when gui.mode=lite

packaging:
  drop_unused_module_folders: true   # required: tarball has no extra applications
```

`packaging.drop_unused_module_folders: true` is mandatory for customer delivery. The pack **must not contain** other `modules/*` (no ticketing if they bought HR only). `opzhubctl doctor --as-prod` fails if extra application folders are present. `requires` may add shared modules (e.g. `notifications`) but never the rest of the 50+ catalog.

## 4. `module.yaml`

See [01 — Plug-and-Play](01-plugin-play-model.md). Additional optional keys:

```yaml
config_schema: ./config.schema.yaml   # validates modules.<id> block
compose_profiles:
  - ocr                               # docker compose profile name
```

## 5. Spring `application.yml` relationship

Spring Boot loads a short `application.yml` that **only** points at the platform file and process concerns (port, virtual threads, graceful shutdown). All DB/cache types live in `platform.yaml` so Python and `opzhubctl` share them.

Forbidden: setting `spring.data.redis.*` as the way feature code reaches cache. Only `ValkeyCacheServer` may bind a driver.

## 6. Frontend public config

The browser must **not** receive `db` or `cache` connection secrets. Nginx or the ERP engine serves a public JSON:

```json
{
  "solutionId": "acme-erp",
  "profile": "prod",
  "modules": ["identity", "ledger"],
  "wsPath": "/ws/opzhub",
  "apiOpzhub": "/api/v1/opzhub",
  "apiAi": "/api/v1/ai",
  "origin": "https://erp.example.com",
  "auth": { "methods": ["password", "oauth2", "face", "fingerprint"] },
  "gui": { "mode": "lite", "allowUserChoice": true }
}
```

Web loads this from the same origin. Flutter uses the same JSON after applying flavor `OPZHUB_ORIGIN` (must match `origin` in prod). Module list is the intersection of disk + manifest, not the full catalog.

## 7. Validation

`opzhubctl doctor` validates:

- JSON Schema of platform + manifest
- `profile=prod` ⇒ `allow_open_dev=false`, `security.tls.mode=required`, `db.postgres.ssl_mode=verify-full`, Valkey TLS on, internal URLs `https://`, no `memory` stores
- `profile=dev` ⇒ memory stores allowed for tests; TLS warn unless `--as-prod`
- Internal HTTP ports are **8114 / 8117 / 8109** (reject 8080, 8000, 8443 in prod YAML)
- `db.type` / `cache.type` / `broker.type` in registry
- `broker.type: kafka` **fails** (optional later; not implemented — [11](11-broker-selection.md))
- `cache.type=memory` or `broker.type=memory` forbidden if profile=prod or replica > 1
- `backup.ftp.enabled` in prod ⇒ `protocol` is `ftps` or `sftp` (not `ftp`)
- enabled modules exist on disk **and** no extra `modules/*` beyond enabled∪requires (customer packs)
- `face` / `fingerprint` in methods ⇒ Python `opzpy` required; `fingerprint.template` ⇒ SourceAFIS models; `face` ⇒ pinned ONNX hashes
- `security.auth.face.liveness` must be `required` when profile=prod
- `face` enabled ⇒ `capture: live`, `client_pad: required` in prod; `face.profile` is `auto`, `medium`, or `high`
- `modules.ocr.inputs` subset of `image`, `pdf`, `doc`, `html` when OCR is packed
- `modules/mail` packed + `profile=prod` ⇒ `mail.send.tls` not off; SMTP/IMAP passwords present if type is smtp/imap ([23](23-mail-send-receive.md))
- `python.vision.quality` is `auto`, `high`, or `medium`
- OCR/face/fingerprint models have OSI or OpenRAIL pins (`doctor license`)
- `profile=prod` ⇒ `license.validate_on_login: true`, `license.live_ms` ≥ 200, `license.hub_url` https
- `gui.mode` is `lite` or `rich`
- `profile=prod` ⇒ no `hang.*` is `0`; `security.errors.debug` is false
- `requires` graph
- Current process is `tsuser` (uid 2100) in prod; not root
- Dependency licenses OSI-compatible (Apache-2.0 project policy)

Fail closed on invalid YAML.

## 8. Environment variable map (v1)

| Variable | Used by |
| -------- | ------- |
| `POSTGRES_PASSWORD` | postgres service + Java/Python servers |
| `POSTGRES_AI_PASSWORD` | Python role |
| `VALKEY_PASSWORD` | valkey + cache + Valkey Streams broker |
| `BACKUP_FTP_USER` / `BACKUP_FTP_PASSWORD` | hourly dump upload (FTPS/SFTP) |
| `PLATFORM_CONFIG_PATH` | default `/etc/opzhub/platform.override.yaml` (site; not in the upgrade zip) |
| `SOLUTION_ID` | logging, tenant default |
| `OPZHUB_PROFILE` | overrides `solution.profile` |
| `MAIL_SMTP_USER` / `MAIL_SMTP_PASSWORD` | Python SMTP when `modules/mail` + `mail.send.type: smtp` |
| `MAIL_IMAP_USER` / `MAIL_IMAP_PASSWORD` | Python IMAP when `mail.receive.type: imap` |

Compose `env_file` is per solution, not in git.
