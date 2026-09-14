# 10 — Solution Composition & Application Catalog

## 1. What a solution is

A **solution** is what one customer runs. It is **not** the full vendor catalog.

- Immutable kernel (`common/`)
- Shared modules they need (`identity`, `admin`, …)
- **Only** the sold applications (e.g. `hr`, or `ticketing`, or both)
- `solution.manifest.yaml` + config + branding
- Infra profiles matching **those** folders

The vendor repo may hold **50+ applications**. Two customers never fork `common/`. They differ by **which `modules/<id>/` folders are in their pack**. Unused applications are **not copied**, not hidden.

See [01](01-plugin-play-model.md) §1.1 for HR vs ticketing vs both.

## 2. Packaging algorithm (required)

```
inputs:  kernel tree, application catalog (50+), solution.manifest.yaml
output:  customer pack (subset)

copy common/, docs/, gateway/, infra/, platform/, tools/
needed = enabled ∪ requires-closure(enabled)
for each id in needed:
    copy modules/<id>/          # fail if missing from vendor catalog
do not copy any other modules/*
assert output modules/* == needed
write solutions/<customer>/
run opzhubctl module-gen on the pack
if clients.mobile:
    flutter build apk/ipa --flavor prod
```

`packaging.drop_unused_module_folders: true` is the default and **required for customer delivery**. A pack that still contains unlisted applications fails `opzhubctl doctor --as-prod`.

## 3. Application catalog

There are two lists. **Do not treat the full list as a default install.**

### 3.1 Shared applications (not sold as products; pulled in when needed)

| ID | Title | Typical `requires` from sold apps |
| -- | ----- | --------------------------------- |
| `identity` | Users, roles, sessions, **RBAC/ABAC**, **OAuth2** | Almost every interactive solution |
| `admin` | Solution settings | Interactive solutions |
| `notifications` | In-app + WS events | When a sold app needs realtime |
| `mail` | Email send + receive (Python SMTP/IMAP; Java `MailPort`) | When a sold app queues mail ([23](23-mail-send-receive.md)) |
| `master-data` | Party, item, UOM, currency, tax | Finance/stock apps |
| `documents` | Files, metadata | OCR / attachments |
| `reporting` | Operational reports | Optional |

Kernel (always, not a module): health, catalog API, DataClient, CacheClient, WS, field kit (web + Flutter), `opzhubctl`, Nginx, Postgres, Valkey.

### 3.2 Sold applications (50+ in the vendor catalog)

Each row is one folder `modules/<id>/`. New customer capability = **new folder**, not a flag in kernel. The table is **illustrative**; the living index is `platform/catalog/applications.yaml` (grows past 50). **None of these are copied unless the manifest lists them** (or `requires` pulls them).

| ID | Title | Runtimes | Requires (typical) |
| -- | ----- | -------- | ------------------ |
| `hr` | Human resources | web, Flutter, Java, scripts | identity, admin |
| `ticketing` | Ticketing / ITSM | web, Flutter, Java, scripts | identity, admin |
| `ledger` | Multi-currency books | web, Flutter, Java, scripts | identity, master-data |
| `inventory` | Stock tables | web, Flutter, Java, scripts | identity, master-data |
| `ocr` | Extract/parse **image, PDF, Office doc, HTML** | web, Flutter, Java, Python, scripts | documents, notifications |
| `image-processing` | Clean/deskew | web, Flutter, Python, scripts | documents |

Further catalog entries (payroll, assets, CRM, helpdesk variants, …) follow the same contract. They are **not** listed here so implementers do not copy them into every pack.

## 4. Reference customer packs

### 4.1 Customer 1 — HR only

```yaml
modules:
  enabled: [identity, admin, hr]
```

On disk: `modules/hr` plus shared. **Absent:** `ticketing`, `ledger`, `ocr`, and every other sold application.

### 4.2 Customer 2 — ticketing only

```yaml
modules:
  enabled: [identity, admin, ticketing]
```

**Absent:** `hr` and the rest of the catalog.

### 4.3 Customer 3 — HR and ticketing

```yaml
modules:
  enabled: [identity, admin, hr, ticketing]
```

Both applications present. Still **no** finance/OCR/inventory unless listed.

### 4.4 Finance-only (another sold subset)

```yaml
modules:
  enabled: [identity, admin, master-data, ledger, notifications, reporting]
```

No HR, no ticketing, no inventory, no OCR.

### 4.5 Kernel appliance (CI)

```yaml
modules:
  enabled: []
```

Smoke: UI shell, health, empty menu. Zero sold applications.

## 5. Cross-cutting enablement matrix

Sold apps use the kernel; they do not pull in sibling sold apps unless `requires` says so. HR does **not** load ticketing code.

| Concern | Kernel | identity | hr | ticketing | ledger | ocr |
| ------- | ------ | -------- | -- | --------- | ------ | --- |
| Field kit | ● | uses | uses | uses | uses | image-drop |
| DataClient | ● | ● | ● | ● | ● | jobs |
| IdentityPort | ● | implements | uses | uses | uses | uses |
| ACID writes | | | ● | ● | ● | forbidden in Python |

## 6. Port wiring (who implements, who consumes)

```
IdentityPort        ← identity        ← all sold apps
BioMatchPort        ← identity (Java) → opzpy mTLS (face / fp template)
NotifyPort          ← notifications   ← hr, ticketing, ocr (if present)
MailPort            ← mail            ← hr, ticketing, identity (optional); Python SMTP/IMAP
MailInboundPort     ← ticketing / hr  ← mail after IMAP persist
HrPort              ← hr              ← (none required)
TicketingPort       ← ticketing       ← (none required)
DocumentsPort       ← documents
LedgerPostingPort   ← ledger
OcrJobPort          ← ocr
```

HR and ticketing **do not import each other**. Customer 3 simply has both folders; menus compose. Consumers depend on **optional ports**. UI hides actions when `ModuleNotPresent`.

## 7. Database schemas per module (logical)

| Schema / prefix | Owner module | Role access |
| --------------- | ------------ | ----------- |
| `kernel_*` | kernel | erp_app |
| `id_*` | identity | erp_app (templates encrypted; Python does not persist) |
| `hr_*` | hr | erp_app |
| `tkt_*` | ticketing | erp_app |
| `md_*` | master-data | erp_app |
| `led_*` | ledger | erp_app |
| `inv_*` | inventory | erp_app |
| `doc_*` | documents | erp_app |
| `ai_*` | ocr / image / documents | ai_app DML, erp_app read |

Physical Postgres is shared; **schemas isolate** drop impact. An HR-only pack never applies `tkt_*` migrations because that folder is absent.

## 8. Frontend menu composition

Kernel sidebar (and Flutter nav) render each present module’s `menu.ts` / `menu.dart`. Example parents: `hr`, `ticketing`, `finance`, `admin`. Missing application = no menu, no routes, no chunks.

## 9. Quality gates before an application is “catalog complete”

A module may be listed in this catalog only if it has:

- `module.yaml` with `requires` / `provides`
- Frontend `register` + at least one page **or** a documented headless flag
- Flutter `register` **or** `runtimes.mobile: false` documented (web-only module)
- Java and/or Python SPI as declared
- `db/` migrations if it owns tables
- Scripts only if ops verbs are needed
- No imports of other modules’ internals
- Field definitions using kernel type keys

## 10. Evolution

- New sold application = **new `modules/<id>/` folder**, added to `platform/catalog/applications.yaml`. It is **not** added to existing customer packs until their manifest lists it.
- New database engine = new `DataServer` + `db.type`, not a new ERP.
- New cache engine = new `CacheServer` + `cache.type`.
- New broker engine = new `BrokerServer` + `broker.type` (Valkey Streams **now**; Kafka optional **later**, not this drop).
- Breaking kernel API = bump `kernel.api_version` and module `compatible_kernel`.

## 11. Implementation sequencing

This design **is** the production implementation contract. Follow it; do not invent a parallel tree. Wrappers (`opzhubctl`, jobs) already exist. Suggested order: [17](17-dev-prod-implementation.md) §2.

1. YAML loader + DataClient/CacheClient/BrokerClient (postgres/valkey **and** memory for tests).
2. Prod Compose + **dev overlay**; Nginx; health; `opzhubctl doctor`.
3. Web shell + Flutter shell + field registry (shared keys).
4. `module-gen` + kernel-only boot (web and Flutter).
5. `identity` + `admin` → first sold app (`hr` **or** `ticketing`, not both by default).
6. Prove three packs: HR-only, ticketing-only, HR+ticketing (`opzhubctl package` extra-folder guard).
7. Further catalog apps one folder at a time.
8. Package tarball **per solution**; staging extract; `profile=prod` smoke.
