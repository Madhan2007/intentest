# Volumes (doc 02 §10, doc 09 §3.1)

| Volume | Mounted at | Purpose |
| ------ | ---------- | ------- |
| `pg-data` | `postgres:/var/lib/postgresql/data` | PostgreSQL data |
| `valkey-data` | `valkey:/data` | Valkey persistence (`appendonly yes`) |
| `etc-opzhub` | `*:/etc/opzhub` | Site config + certs (one-time; not replaced on upgrade) |
| `var-lib-opzhub` | `*:/var/lib/opzhub` | Runtime state, pidfiles, backups |
| `var-log-opzhub` | `*:/var/log/opzhub` | Service + job logs |
| `ocr-models` | *(not created — no OCR module packed)* | Reserved for the `ocr` profile |
| `identity-models` | *(not created — face/fingerprint-template off)* | Reserved for the `ai` profile |

On a real EC2 host these three `opzhub` volumes are ordinarily host bind
mounts (`/etc/opzhub`, `/var/lib/opzhub`, `/var/log/opzhub`) so `opzhubctl`
running on the host and the containers see the same files. `infra/docker-compose.yml`
uses **named volumes by default** so the stack is portable to a laptop
without pre-existing host paths, but every mount is parameterized:

```yaml
volumes:
  - ${OPZHUB_ETC_SRC:-etc-opzhub}:/etc/opzhub
  - ${OPZHUB_VARLIB_SRC:-var-lib-opzhub}:/var/lib/opzhub
  - ${OPZHUB_VARLOG_SRC:-var-log-opzhub}:/var/log/opzhub
```

Set `OPZHUB_ETC_SRC=/etc/opzhub`, `OPZHUB_VARLIB_SRC=/var/lib/opzhub`,
`OPZHUB_VARLOG_SRC=/var/log/opzhub` in `infra/.env` (see
[`infra/.env.example`](.env.example)) to switch to real host paths for
production / EC2 — see [USAGE.md](../USAGE.md) mode 4. The `init` and
`certs` one-shot services are idempotent either way: against named
volumes they seed a fresh stack; against already-populated host paths
(from `infra/ec2-bootstrap.sh`) they simply no-op.
