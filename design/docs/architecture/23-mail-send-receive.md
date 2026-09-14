# 23 — Mail send and receive (Python protocol, Java caller)

**SMTP, IMAP, and MIME are Python-only.** The Java engine never opens a mail socket. When a sold app needs email, **Java calls Python** (mTLS or a broker job). Inbox/outbox **rows** stay ACID in Java via `DataClient`.

Send and receive are **one common feature**, not copies under HR or ticketing. This document is **design only**.

`notifications` remains **in-app / WebSocket**. Mail is **Internet email**.

## 1. Split of work

| Layer | Does | Does not |
| ----- | ---- | -------- |
| Java `MailPort` | Outbox/inbox metadata, RBAC, templates ids, inbound hooks to sold apps | Jakarta Mail, SMTP, IMAP, raw MIME parse |
| Python `MailClient` + workers | SMTP send, IMAP poll, MIME build/parse, TLS to the mail host | Issue sessions; post ledgers; talk to the browser |
| Browser / Flutter | Kernel `Collection` + `Form` on `modules/mail` screens | SMTP credentials, `/api/v1/ai/mail/**` |
| Sold app (HR, ticketing, …) | `mailPort.queue("hr.offer", payload)` or inbound handler | Own SMTP class or inbox poller |

```
Sold app / identity
        │ MailPort.queue / list
        ▼
   Java (opzbe)     ── persist mail_out / mail_in ──► Postgres
        │ mTLS POST /api/v1/ai/mail/send
        │ or BrokerClient "mail.send" / "mail.receive"
        ▼
  Python (opzpy / opzhub-be-core)
        │ SmtpMailServer / ImapMailServer
        ▼
   Customer SMTP / IMAP (TLS)
```

Inbound reverse: IMAP → Python parse → mTLS `POST https://opzhub-be-app:8114/api/v1/opzhub/mail/inbound` → Java stores + `MailInboundPort` (optional ticket/HR) + `NotifyPort`.

Public Nginx **404s** `/api/v1/ai/mail/` (same pattern as bio).

## 2. Folder structure (common + one shared module)

### 2.1 Kernel — protocol adapters (always with Python kernel)

```
common/python/opzhub_kernel/mail/
├── client.py                 # MailClient (send_job, fetch_idle) — no smtplib in features
├── types.py                  # Envelope, Addr, Part, MailError
├── send/
│   ├── port.py               # MailSendServer
│   ├── smtp.py               # STARTTLS / implicit TLS
│   └── memory.py             # tests
├── receive/
│   ├── port.py               # MailReceiveServer
│   ├── imap.py               # IMAP4_SSL poll / IDLE
│   └── memory.py
└── mime/
    ├── build.py              # multipart, CID, 7bit/base64
    ├── parse.py              # inbound; strip scripts
    └── allowlist.py          # MIME types for attachments
```

YAML `mail.send.type` / `mail.receive.type` select the server (`smtp` / `imap` / `memory`). Future `graph` is a new server package, same client.

### 2.2 Shared module — product mail (send + receive together)

```
modules/mail/                          # SHARED — not a sold SKU; pulled by requires
├── module.yaml
├── README.md
├── forms/                             # FormEnvelope catalogs (doc 22)
│   ├── outbox.list.yaml
│   ├── compose.yaml
│   ├── inbox.list.yaml
│   └── message.view.yaml
├── contracts/
│   ├── openapi.yaml
│   └── events.yaml                    # mail.out.{id}, mail.in.{id}
├── db/
│   ├── 0001_mail_init.sql             # mail_out, mail_in, mail_acct, mail_tpl
│   └── rollback/
├── frontend/                          # kernel Collection + Form only
│   ├── index.ts
│   ├── routes.ts                      # /app/mail/...
│   ├── menu.ts
│   └── pages/                         # no SMTP UI secrets
├── mobile/
├── backend/                           # Java
│   └── .../mail/
│       ├── MailAutoConfiguration
│       ├── api/                       # public /api/v1/opzhub/mail/** (session)
│       ├── application/               # MailPort impl; inbound dispatcher
│       └── adapter/
├── python/
│   ├── plugin.py
│   ├── routers/
│   │   ├── send.py                    # POST /api/v1/ai/mail/send  (mTLS, Java)
│   │   └── health.py                  # SMTP/IMAP ping for doctor
│   ├── workers/
│   │   ├── send.py                    # queue mail.send
│   │   └── receive_poll.py            # queue mail.receive
│   ├── pipelines/
│   │   ├── mime_build.py
│   │   └── mime_parse.py
│   └── templates/                     # built-in kernel-ish templates (ids only)
│       └── system/
│           └── test_message.yaml
└── scripts/
    └── commands/
        └── mail-doctor.py
```

Sold apps **do not** grow `python/smtp.py`. They may add **templates only**:

```
modules/<id>/mail-templates/
  offer.yaml                 # id: hr.offer — discovered by mail module
```

`module.yaml` for HR: `requires: [identity, admin, mail]` when that pack sends mail. Ticketing the same for inbound-to-ticket.

### 2.3 What is not a second mail stack

| Forbidden | Why |
| --------- | --- |
| `modules/hr/python/send_mail.py` | Common module owns protocol |
| Java `JavaMailSender` in ledger/HR | Call `MailPort` |
| Browser → SMTP or `/api/v1/ai/mail/` | Nginx 404 |
| `notifications` implementing IMAP | Different channel |

## 3. Java API (caller)

```
# session (compose / list / retry) — RBAC feature ids on modules/mail
GET/POST /api/v1/opzhub/mail/out
GET      /api/v1/opzhub/mail/in
POST     /api/v1/opzhub/mail/out/{id}/retry

# internal mTLS from Python only
POST /api/v1/opzhub/mail/inbound
```

`MailPort` (kernel interface, implemented by `modules/mail`):

```
queue(templateId, to[], data, opts) -> out_id
retry(out_id)
```

Sold application services inject `MailPort`. If `modules/mail` is absent, inject `Optional<MailPort>` and hide compose / skip send.

Inbound: `MailInboundPort` implementations registered by ticketing/HR (optional). Mail module calls all present ports; unknown template → store inbox only.

## 4. Python API (protocol)

Internal only (Java → `https://opzhub-be-core:8117`):

```
POST /api/v1/ai/mail/send
     { "out_id", "from", "to", "cc?", "subject", "text?", "html?", "parts[]" }
     → { "ok", "smtp_id" } or enqueue { "job_id" }

POST /api/v1/ai/mail/receive/poll     # optional kick; worker also cron/broker
GET  /api/v1/ai/mail/health           # smtp/imap reachability
```

Default: **enqueue** `mail.send` / `mail.receive` (same as OCR). Tiny transactional mail may use the sync send route with `mail.send.sync_timeout_ms` (fail closed).

Workers use `MailClient` only — never `smtplib` in `modules/hr`.

## 5. Send

1. Java validates recipients + template + `FormPort` if the user composed from UI (`req`/`rules` from envelope).
2. Insert `mail_out` (`queued`).
3. Call Python (HTTP or broker).
4. Python `mime.build` → `SmtpMailServer.send` (TLS).
5. Python reports status; Java sets `sent` / `failed` + `NotifyPort`.
6. Idempotency: `out_id` is the key; worker is safe to retry.

Attachments: blob refs already in `documents` (or inline cap `mail.max_part_kb`). Python reads via volume/internal API, not from the browser.

## 6. Receive

1. `receive_poll` worker: IMAP SEARCH UNSEEN (or IDLE if capability).
2. `mime.parse`: allow-listed parts; HTML sanitize (no script; **no outbound fetch** — same SSRF bar as OCR HTML).
3. Dedup on `Message-ID` + account + tenant.
4. POST Java inbound; Java inserts `mail_in`, optional `MailInboundPort`.
5. Mark IMAP seen only after Java `ok` (or move to `mail.receive.done_mailbox`).

Cron: `opzhub-run-cron` may publish `mail.receive` on an interval ([16](16-jobs-crontab.md)); lock + timeout. Do not overlap pollers.

## 7. YAML

```yaml
mail:
  enabled: true                   # ignored if modules/mail absent
  send:
    type: smtp                    # smtp | memory
    sync_timeout_ms: 8000
    host: smtp.example.com
    port: 587
    tls: starttls                 # starttls | implicit | (dev) off
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
```

Prod: TLS required; empty password fails `opzhubctl doctor`. `memory` only when `profile: dev` or tests.

## 8. Access and UI

`modules/mail` features (login matrix, short ids): `out`, `in`, `acct` (admin accounts). Letters `v c u d`. Compose = `c` on `out`. Import/export of mail rows follows [22](22-common-fields-forms-fk.md) §8 if the list catalog enables `io`.

Kernel field kit: `email`, `textarea`/`richtext` body, `attachments`. No app-specific mail composer in HR.

## 9. Processes

| Need | Start |
| ---- | ----- |
| `modules/mail` on disk | `opzpy` / `opzhub-be-core` (send + IMAP poll) |
| Mail + no OCR | still both Python processes; no Paddle |

`opzhubctl` treats `modules/mail` like OCR for **workers**, and like bio/OCR for **API**.

## 10. What must not happen

- SMTP/IMAP in Java or in a sold application folder.
- A second send stack in `notifications`.
- Public proxy of `/api/v1/ai/mail/**`.
- Storing SMTP passwords in the SPA or in git.
- Executing inbound HTML/JS or fetching remote images at parse time.
- Python writing `ledger_*` because a remittance mail arrived (Java inbound + ledger module only).
- Marking IMAP Seen before Java persisted the row (loss on crash).
