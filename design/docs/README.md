# ManageMyOpz (opzhub) — Design Specification

**Status:** Production architecture. Implement against these contracts. Wrappers (`opzhubctl`, jobs, init-system) exist; application runtimes are built next.  
**Audience:** Principal engineers, solution architects, and implementation teams.  
**Deployment target:** Docker Compose on AWS EC2 (Ubuntu 24.04 LTS). **Development** uses the same tree with `profile: dev` ([17](architecture/17-dev-prod-implementation.md)).  
**Composition model:** Plug-and-play **applications**. Vendor catalog is 50+ apps; each customer pack is kernel + shared modules + **only the apps they bought** (HR, ticketing, both, or any other subset).  
**Clients:** Web SPA (React) and mobile (**Flutter**), same APIs and field keys.

This folder is the single source of truth for architecture. Read documents in order for a first pass; use them independently after that.

| Order | Document | Purpose |
| ----- | -------- | ------- |
| 00 | [System Overview](architecture/00-system-overview.md) | Problem, principles, runtime topology, tech stack, quality attributes |
| 01 | [Plug-and-Play Model](architecture/01-plugin-play-model.md) | 50+ apps; deliver only what was bought (HR vs ticketing vs both) |
| 02 | [Repository Folder Structure](architecture/02-repository-folder-structure.md) | Canonical tree for common kernel, modules, solutions, and infra |
| 03 | [Frontend Design](architecture/03-frontend-design.md) | Web + Flutter; field kit; GUI `lite` vs `rich` skins |
| 04 | [Backend Java Design](architecture/04-backend-java-design.md) | Spring Boot ERP engine, module SPI, ACID transactions, WebSockets |
| 05 | [Backend Python Design](architecture/05-backend-python-design.md) | FastAPI AI/OCR engine, workers, CV pipelines, identity bio match |
| 06 | [Scripts Design](architecture/06-scripts-design.md) | Shared CLI kernel and removable operational/feature scripts |
| 07 | [Data & Cache Client–Server Model](architecture/07-data-cache-client-server.md) | DB/cache/broker abstraction, YAML type flags, swap path |
| 08 | [Configuration YAML](architecture/08-configuration-yaml.md) | Platform, solution, and module config contracts |
| 09 | [Docker, Nginx & EC2](architecture/09-docker-nginx-ec2.md) | Compose topology, reverse proxy, Ubuntu bootstrap |
| 10 | [Solution Composition](architecture/10-solution-composition.md) | Customer packs; application catalog; HR / ticketing examples |
| 11 | [Broker Selection](architecture/11-broker-selection.md) | Valkey Streams (v1); Kafka optional later, **not implemented** this drop |
| 12 | [Transport Security](architecture/12-transport-security-tls.md) | HTTPS/WSS and TLS on every hop |
| 13 | [Open Source Licensing](architecture/13-open-source-licensing.md) | Apache-2.0 project, OSI allow-list, NOTICE/SBOM |
| 14 | [Performance & Memory](architecture/14-performance-memory.md) | Production bounds, leak prevention, high-throughput rules |
| 15 | [Non-Root Execution](architecture/15-non-root-execution.md) | tsuser, `/home/tsuser/opzhub` only |
| 16 | [Jobs & crontab](architecture/16-jobs-crontab.md) | Lock + timeout wrappers; no overlapping hangs |
| 17 | [Dev & prod implementation](architecture/17-dev-prod-implementation.md) | Production ship shape; development overlay; promotion path |
| 18 | [Identity, RBAC, ABAC, OAuth2, biometrics](architecture/18-identity-rbac-abac-oauth2.md) | Compact access matrix; password, OIDC, face, fingerprint |
| 19 | [DB backup, recovery, migrate](architecture/19-db-backup-migrate.md) | Hourly local/FTP dump; schema create/update without wiping data |
| 20 | [Licensing, site vs central](architecture/20-licensing-site-central.md) | Central license server; live check while typing; password type entitled before login |
| 21 | [Testing application](architecture/21-testing-application.md) | Separate QA repo layout: unit, functional, performance (design only; no test files in this drop) |
| 22 | [Common fields, forms, FK](architecture/22-common-fields-forms-fk.md) | Kernel field kit; form policy from BE; FK; import/export/bulk CUD |
| 23 | [Mail send and receive](architecture/23-mail-send-receive.md) | Python SMTP/IMAP/MIME; Java MailPort caller; common `modules/mail` tree |
| 24 | [Hang prevention & errors](architecture/24-hang-prevention-error-reporting.md) | Timeouts/locks/deadlines; correlation_id error contract for debug |

## Non-goals of this design drop

- Shipping the full 50+ application catalog to a customer. Packs are a **subset** ([01](architecture/01-plugin-play-model.md), [10](architecture/10-solution-composition.md)).
- Web stack is React + TypeScript; mobile stack is **Flutter**. Both share `FieldDef` and public APIs.
- No implementation of OCR models, ledger math, or inventory algorithms — only their module boundaries.
- Wrapper scripts under `infra/wrappers/` are implemented (process start/stop via `opzhubctl`, `run-job.sh` / crontab).
- No pytest, Locust, or JUnit files in this drop. The QA tree is specified only in [21](architecture/21-testing-application.md).
- **Kafka is optional and not implemented.** Job queues are Valkey Streams. Do not add a Kafka broker, Compose service, or client libraries ([11](architecture/11-broker-selection.md)).

## How to use this design later

1. Pick a customer solution from the composition catalog.
2. Copy `common/` unchanged (web + Flutter kernels).
3. Copy only the `modules/*` folders listed in that solution’s manifest.
4. Bind `db.type`, `cache.type`, and `broker.type` in YAML (`broker.type: valkey` for this drop; Kafka remains a later optional swap).
5. Develop with `profile: dev` + Compose overlay; ship with `profile: prod` ([17](architecture/17-dev-prod-implementation.md)).
6. Implement against the contracts in documents 03–09 and 17.
