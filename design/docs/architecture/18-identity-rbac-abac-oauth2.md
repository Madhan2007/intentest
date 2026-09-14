# 18 — Identity, Login, RBAC, ABAC & biometrics

The `identity` module owns authentication and authorization. Kernel enforces the decision on every request. Clients receive a **small access matrix at login**, not the full policy catalog.

Plug-and-play: if `hr` is not in the customer pack, it does not appear in the matrix, in roles UI, or in permission rows. Fifty applications in the vendor catalog must not inflate the login payload.

## 1. Goals

| Goal | Rule |
| ---- | ---- |
| RBAC | Roles grant **actions** on an **app** and on **features inside that app** (employees, leave, incidents, …). |
| ABAC | Attributes (org, dept, owner, …) **filter rows and refine allow/deny** on the server — not in the login JSON. |
| Compact login | Session API returns **short letters** (`v c u d a`) per **feature**, only where allowed. |
| No flood | No full words, no denied features, no ABAC policies, no apps/features not in this pack. |
| OAuth2 | Login supports **OAuth 2.0 / OIDC** (authorization code + PKCE) in addition to local password. |
| Face / fingerprint | Optional login. **Client** rejects photos and phone-replayed video; **Python** is the matcher of record. Two face profiles: `high` / `medium` ([05](05-backend-python-design.md) §13). |
| Company login (hub) | While typing `company/user`, live-check the **central license server**. Password (and other methods) appear and succeed only if that license entitles them ([20](20-licensing-site-central.md)). |
| Never trust the client | The matrix is for **UI hide/show**. Every API call re-evaluates RBAC+ABAC. |

## 2. Login methods

On a **site** appliance, username + an enabled method is enough to parse identity, but the **central license server** still live-checks the instance and must entitle `password` (etc.) before that field is shown or accepted ([20](20-licensing-site-central.md)).

On the **central hub URL**, identity is two steps:

1. **Live** `POST /api/v1/opzhub/license/resolve` `{ "q": "acme/ada", "live": true }` while typing (debounced). Hub asks the **central license server**; response includes `auth[]` ([20](20-licensing-site-central.md) §2.1).
2. Password, OAuth2, face, or fingerprint **on `gui`**, and only if that method is in `auth[]`. For both central DB subtypes, `gui` is the hub. Navigate away only when `stack=site`.
3. On password (or any method) **submit**, Java calls license `phase: login` + `method` **before** Argon2id / OAuth / bio. The secret never leaves the identity BE.

`security.auth.methods` is a list. At least one must be enabled. Prod forbids empty and forbids password-only weak defaults without lockout.

| Method | Id | Flow |
| ------ | -- | ---- |
| Local password | `password` | `POST /api/v1/opzhub/identity/login` |
| OAuth 2.0 / OIDC | `oauth2` | Authorization Code + **PKCE** (web and Flutter) |
| Face | `face` | Live camera + client PAD + challenge → Java → Python verify → same session |
| Fingerprint | `fingerprint` | WebAuthn (platform) and/or scanner template → Java; template match in Python |

All methods end in the **same** opzhub session (cookie and/or Bearer). The IdP access token is **not** forwarded to feature APIs. Biometric scores are **not** forwarded to the browser.

### 2.1 Password

Show the password field only after live resolve `st=ok` **and** `auth` contains `password` ([20](20-licensing-site-central.md)). Optional `POST …/license/preflight` `{ "co", "method": "password" }` when the field is focused.

```
POST /api/v1/opzhub/identity/login
{ "username": "...", "password": "..." }
```

Java, in order:

1. License server `validate` `phase: login`, `method: password` (`validate_on_login`). Fail closed in prod if the server is down (site: lease within `grace_days`).
2. If `password` is not entitled → generic fail (dummy-hash for timing).
3. Argon2id compare, lockout, audit in `identity`.
4. Session envelope (§4).

The license server never receives the password bytes. YAML `security.auth.password.enabled` cannot override a license that omitted `password`.

### 2.2 OAuth2 / OIDC

```
GET  /api/v1/opzhub/identity/oauth2/{provider_id}/start?pkce_challenge=...
     → license `phase: login` `method: oauth2` first; then 302 to IdP
GET  /api/v1/opzhub/identity/oauth2/{provider_id}/callback?code=...&state=...
     → exchange code, map claims → local user, issue opzhub session, 302 to SPA
GET  /api/v1/opzhub/identity/oauth2/providers
     → public list: id, label, start_path  (no secrets, no full IdP metadata dump)
```

| Setting | Rule |
| ------- | ---- |
| Grant | Authorization Code only. No implicit. Resource-owner password grant **off**. |
| PKCE | **Required** for SPA and Flutter (`S256`). |
| Confidential client | `client_secret` only on the Java engine (server). Never in the browser or mobile binary. |
| Flutter | AppAuth / `flutter_appauth`; redirect `com.managemyopz.app:/oauth2/callback` (solution-branded scheme). |
| Web | Redirect through Nginx same origin; then SPA reads `GET /identity/session`. |
| User mapping | `sub` / `email` → `id_user`. First login may JIT-provision if `oauth2.jit: true` (default false in prod). |
| Scopes | `openid profile email` default. Extra scopes are not copied into the access matrix. |

Multiple providers (`company-sso`, `azure`, …) are YAML entries. Login page shows **one button per provider** from the public providers list — not the token endpoint, JWKS, or client secret.

### 2.3 Face login (live only, client PAD + Python match)

Login is **verify**, not search: the user already typed `acme/ada` (or site username). The gallery is that user’s enrolled face template only.

**Capture is live camera only.** Reject printed photos, still JPEGs, gallery picks, clipboard, and **motion pictures** (a video or another phone played in front of the camera). Client-side logic runs **before** upload. Server PAD + match still run; the client cannot issue a session.

```
GET  /api/v1/opzhub/identity/bio/challenge
     → { "n": "<nonce>", "ttl": 60, "hint": "face", "profile": "medium",
         "acts": ["blink", "right"] }
POST /api/v1/opzhub/identity/login/face   (multipart)
     n, username, frames[], proof
```

`acts` is a **server-chosen** challenge (subset of blink / left / right / nod). Client must show that motion in the live stream; a static photo cannot comply.

#### Client (web + Flutter) — deduct only a real-time face

| Check | Rule |
| ----- | ---- |
| Source | **Web:** `getUserMedia`. **Flutter (Android + iOS):** `camera` front lens. No `<input type=file>`, no gallery, no `image_picker` for login or enroll. |
| Stream | Live track / `CameraController`. Drop if frozen or not `isStreaming`. |
| Landmarks | **Web:** MediaPipe-class WASM. **Flutter:** MediaPipe / pinned TFLite on an Isolate (not the UI isolate). Same challenge semantics. |
| Photo reject | No optical flow / landmark delta over ≥ `face.client.min_frames` → fail locally; do not upload. |
| Replay reject | Screen/moiré, rectangular bezel, specular “phone glass”, flat depth if available → fail locally. |
| Challenge | User must complete `acts` in order within TTL; timestamps go in `proof`. |
| Upload | Only after client PAD **pass**. Send N JPEG frames (not a movie) + `proof` JSON. |

The client **does not** decide “this is Ada”. It only answers “this stream is a live person matching the challenge”. Matcher of record stays Python.

`gui.mode` (lite/rich) does not skip PAD. Lite still uses the camera; it just skips decorative art.

#### Two processing profiles (`high` / `medium`, open source)

Same ids on **web WASM, Flutter TFLite, and Python**. Server YAML `python.vision.quality: auto` picks **high** when RAM/CPU allow, else **medium** ([05](05-backend-python-design.md) §7.2). `security.auth.face.profile` may pin. Challenge returns the **resolved** id. A slow **phone** must not silently switch `high` → `medium` (offer password/OAuth). All engines OSI/OpenRAIL; no closed SaaS.

| Profile | `medium` | `high` |
| --- | --- | --- |
| Aim | Medium accuracy, medium CPU | Higher accuracy, higher CPU |
| Client frames | 3–5, one active act | 8–12, two+ acts, tighter motion |
| Client PAD | Landmarks + flow + light screen-PAD (WASM **or** Flutter TFLite) | Same + heavier MiniFASNet-class on-device |
| Server detect | YuNet / small SCRFD | SCRFD, stricter one-face |
| Server PAD | Passive MiniFASNet | Passive + challenge replay check on `proof` |
| Embed | Distilled ArcFace (e.g. 128–256-d) | Full ArcFace-class **512-d** |
| `far_target` | `1.0e-3` default | `1.0e-4` default (YAML may tighten) |
| Python timeout | `bio_timeout_ms` ~600 | ~1500 |
| When | `python.vision.quality` auto on a small host, or pin `medium` | Default when the host can hold high models |

`profile: high` still has a hard timeout and `bio_max_inflight`. Do not run PaddleOCR in this path.

#### Java then Python

1. License `phase: login` `method: face`. Consume nonce. Reject replay.
2. Reject if `proof.acts` ≠ challenge, or frame count outside the profile band, or client flagged fail.
3. Lockout shared with password.
4. Load that user’s template (`DataRouter` on hub).
5. Internal Python `POST /api/v1/ai/identity/bio/face/verify` (mTLS) with frames + `profile`. **Re-run PAD** — do not trust client `ok`.
6. On `{ "ok": true }` issue the same session as password (§4).
7. Audit `method=face` without storing frames.

Python pipeline ([05](05-backend-python-design.md) §13): quality → PAD (print **and** screen-replay) → align → embed → cosine at the profile’s FAR. Fail closed on spoof, blur, or small face.

Enrollment uses the **same** live capture + client PAD (not a mugshot file). Python returns a template; Java stores ciphertext. Consent required.

### 2.4 Fingerprint login

Two backends, one method id `fingerprint`. YAML `fingerprint.mode` is a list.

| Mode | Where the print lives | Matcher |
| ---- | --------------------- | ------- |
| `platform` | Device only (Windows Hello, Touch ID, Android) | **WebAuthn** in Java (public key). Python is unused. |
| `template` | Minutiae template in `id_*` (never raw image) | **Python SourceAFIS** 1:1 vs claimed user |

**Platform (healthy default on phones/laptops):** browser and Flutter cannot read fingerprint pixels. Use WebAuthn:

```
POST /api/v1/opzhub/identity/webauthn/login/start
POST /api/v1/opzhub/identity/webauthn/login/finish
POST /api/v1/opzhub/identity/webauthn/register/start|finish   # enrolled session
```

Resident keys allowed so the user may omit username on that device. Same session envelope.

**Template (kiosk / certified scanner):** same challenge nonce as face, then:

```
POST /api/v1/opzhub/identity/login/fingerprint
{ "username": "ada", "n": "<nonce>", "tpl": "<scanner template or png>" }
```

Java forwards to Python `…/bio/fp/verify`. Quality (NFIQ-class) → liveness flag from sensor when present → minutiae extract → SourceAFIS match at YAML threshold. Prod forbids storing WSQ/PNG after enroll.

### 2.5 Public bootstrap (not the session)

`GET /api/v1/opzhub/meta/auth` is the **pack/YAML default** before a company is known (hub splash). After live resolve, the UI uses **`auth[]` from the license server**, not this list.

```json
{
  "methods": ["password", "oauth2", "face", "fingerprint"],
  "oauth2": [
    { "id": "company-sso", "label": "Company SSO" }
  ],
  "bio": {
    "face": true,
    "profile": "medium",
    "capture": "live",
    "fingerprint": ["platform", "template"]
  }
}
```

Do **not** put IdP client secrets, JWKS, or role catalogs here.

## 3. RBAC and ABAC (server model)

### 3.1 RBAC (app + internal features)

| Object | Meaning |
| ------ | ------- |
| User | Person or service account |
| Role | Named set of grants (`hr.manager`, `tkt.agent`) |
| Grant | `app` + **`feature`** + `action` |

**Actions on the wire — one letter, never a word:**

| Letter | Meaning |
| ------ | ------- |
| `v` | view / read / open |
| `c` | create |
| `u` | update |
| `d` | delete |
| `a` | approve / post / assign / workflow |

Import / export / bulk **reuse these letters** (no `i` / `e` keys): export → `v`; import create / bulk create → `c`; import update / bulk update → `u`; bulk delete → `d`. Catalog + FormEnvelope may still hide import on money screens.

Packed as a **string** of letters, not a JSON array: `"vcua"` not `["view","create","update","approve"]`.

**Features** are minor capabilities **inside** an application (not the whole HR product). Each module declares a **short id** (2–4 chars) in `module.yaml`. Those ids are the only keys allowed in the login matrix.

```yaml
# modules/hr/module.yaml
access:
  features:
    - { id: emp, title: Employees }     # title is admin UI only — not in login JSON
    - { id: lv,  title: Leave }
    - { id: pay, title: Payroll }
    - { id: att, title: Attendance }

# modules/ticketing/module.yaml
access:
  features:
    - { id: inc, title: Incidents }
    - { id: sl,  title: SLA }
    - { id: kb,  title: Knowledge base }
```

Grant examples: `hr.emp.v`, `hr.lv.a`, `tkt.inc.cu`. `*` means “whole app default” when a feature has no row of its own.

Hard cap: **32 features per installed app**. Field-level / button-level flags are **not** login-matrix keys. They inherit the parent feature’s letters, then ABAC + optional field-policy rows on the server. The **FormEnvelope** returns effective `req` / `mode` / `rules` per field ([22](22-common-fields-forms-fk.md)). The client kernel applies those generically — it does not contain sold-app names.

Stale grants for apps or feature ids not on disk are ignored.

### 3.2 ABAC (not sent to the client)

Policies attach to `app.feature.action`: *allow `hr.emp.u` if `resource.dept_id == user.dept_id`*.

Attributes (examples):

| Source | Examples |
| ------ | -------- |
| User | `org_id`, `dept_id`, `location_id`, `manager_of[]` |
| Resource | `owner_id`, `dept_id`, `status`, `sensitivity` |
| Environment | `now`, `ip_cidr` (optional) |

Evaluation:

1. RBAC: grant for `app` + `feature` + letter (`u`)?
2. If no grant → deny.
3. If grant has no ABAC → allow (still tenant-scoped).
4. If grant has ABAC → evaluate user + resource + env. Deny closed on missing attributes.

**Row filters** stay on the server (`dept_id = :user_dept` via `DataClient`). Login never returns allowed id lists.

Feature modules call:

```
access.require("hr", "emp", "u", Map.of("dept_id", employee.deptId));
access.rowFilter("hr", "emp", "v");
```

Python/AI does not embed a second policy engine.

## 4. Login / session API — compact matrix

```
POST /api/v1/opzhub/identity/login
POST /api/v1/opzhub/identity/login/face
POST /api/v1/opzhub/identity/login/fingerprint
GET  /api/v1/opzhub/identity/session
POST /api/v1/opzhub/identity/logout
```

**Wire format (default):** one object `a`. App id → feature id → **letter string**. Only granted letters. Omit apps and features with nothing.

```json
{
  "user": { "id": "u_01", "n": "Ada", "r": ["hr.mgr"] },
  "a": {
    "hr":  { "*": "vcua", "emp": "vcu", "lv": "va" },
    "tkt": { "*": "vc",   "inc": "vcu" }
  }
}
```

Read: HR app default `vcua`; employees `view+create+update`; leave `view+approve`; no `pay` key ⇒ no payroll UI. Ticketing incidents `vcu`. Words like `view` / `create` are **never** on this API.

Same user with **bits** (`security.access.matrix_format: bits`, `v=1,c=2,u=4,d=8,a=16`):

```json
"a": { "hr": { "*": 23, "emp": 7, "lv": 17 } }
```

### 4.1 Anti-flood rules

| Include | Exclude |
| ------- | ------- |
| Installed apps only | Other catalog apps |
| Features declared in that module’s `access.features` | Undeclared screen/button/field ids |
| Features where the user has at least `v` | Features with zero letters |
| Letter string or small int | `"view"`, `"create"`, titles, descriptions |
| Role **ids** (`r`) | Role trees, ABAC JSON, policy documents |
| | Allowed primary-key lists (ABAC rows) |

Size is `O(installed_apps × granted_features)`, each value ≤ 5 chars. An HR-only pack never mentions `tkt`.

Client kernel: `has(app, feature, letter)` where `app`/`feature` are **data** from menu/route register or from `FormEnvelope.access`. Kernel source must not hardcode `"hr"` / `"emp"`. Forms prefer envelope `mode`/`req` over re-deriving from letters.

Server still `require(app, feature, "u", attrs)` on every write.

### 4.2 Fine check (one resource, still tiny)

```
POST /api/v1/opzhub/identity/access/check
{ "app": "tkt", "f": "inc", "x": "a", "id": "t_99" }
→ { "ok": true }
```

`x` is the action letter. Do **not** add a dump endpoint. Do not return every approvable ticket.

### 4.3 Form policy (not the login matrix)

Mandatory / optional / free-text rules / per-field `edit|view|hide` / **import-export-bulk flags** travel on:

```
GET /api/v1/opzhub/forms/{form_id}?mode=edit&id=
```

List forms include `io` and `bulk`. Same user, same RBAC+ABAC evaluator. Cache by form version + role set. Do **not** attach the full field catalog to `GET /identity/session` (anti-flood). Detail: [22](22-common-fields-forms-fk.md).

## 5. Where state lives

| Data | Store |
| ---- | ----- |
| Users, roles, grants, ABAC policy docs | Postgres via `DataClient` (`id_*` schema) |
| Session | `CacheClient` `session:{id}` TTL = `security.session_ttl_seconds` |
| Password hashes | Postgres; never cache plaintext |
| Face / fingerprint templates | Postgres `id_bio_*`; **encrypted**; never cache raw images |
| OAuth2 client secret | `/etc/opzhub/secrets.env` |
| Bio challenge nonce | `CacheClient` short TTL, single use |

Session cache holds `user_id`, `roles`, **precomputed matrix** (invalidated on role change). ABAC resource checks still hit the evaluator with current resource attrs.

## 6. HTTP and clients

- Web: HTTP-only **Secure** cookie after login/callback.
- Flutter: Bearer from secure storage (same session id).
- Kernel accepts cookie **or** `Authorization: Bearer`.
- `/api/v1/ai/**` and `/ws/opzhub`: same session; WS first message authenticates.
- CORS: not needed for same-origin web; Flutter uses public origin only.

Kernel filter: unauthenticated → 401. Authenticated but RBAC deny → 403. ABAC deny → 403 (same; do not leak why in prod).

## 7. YAML

```yaml
security:
  session_ttl_seconds: 28800
  auth:
    methods: [password, oauth2, face, fingerprint]   # subset allowed
    password:
      enabled: true
      lockout_attempts: 8
    face:
      enabled: true
      profile: auto                 # auto | medium | high; auto follows python.vision.quality
      far_target: 1.0e-4            # used when resolved quality is high; medium uses 1.0e-3
      liveness: required            # required | optional (dev only)
      client_pad: required          # required in prod; live camera + anti-photo/replay
      capture: live                 # live only; file/gallery forbidden
      min_frames: 3                 # medium; high uses 8
      max_image_kb: 400
      max_frames_kb: 1200
    fingerprint:
      enabled: true
      mode: [platform, template]    # platform = WebAuthn; template = Python
      max_image_kb: 200
    oauth2:
      enabled: true
      jit: false                    # prod default
      providers:
        - id: company-sso
          type: oidc
          issuer: https://idp.example.com
          client_id: ${OAUTH_CLIENT_ID}
          client_secret: ${OAUTH_CLIENT_SECRET}
          scopes: [openid, profile, email]
          pkce: true
  access:
    matrix_format: letters          # letters (default) | bits
    # letters: "vcua"  bits: v=1 c=2 u=4 d=8 a=16
```

`opzhubctl doctor`: prod requires TLS; OAuth2 `client_secret` present if `oauth2` enabled; `methods` non-empty; `allow_open_dev` false; `face` / `fingerprint.template` ⇒ `opzpy` healthy and pinned **open-source** bio models; `liveness: optional` and `client_pad` not `required` illegal in prod; `face.profile` is `auto`, `medium`, or `high`; `face.capture` is `live`; `license.validate_on_login: true`; `license.live_ms` ≥ 200.

## 8. Module contract

`modules/identity/` owns login UI (password, OAuth2, face camera, fingerprint), WebAuthn, admin of users/roles **for installed apps only**, and Python bio pipelines under `modules/identity/python/`.

Other modules:

- Declare `access.features` (short ids) in `module.yaml`.
- Call `AccessPort.require(app, feature, "u", attrs)` in application services.
- Menus/buttons map to a **feature id**, not a new letter.

If `identity` is absent: only `security.allow_open_dev` (forbidden in prod).

## 9. What must not happen

- Login JSON with `"view"`/`"create"` words, denied features, ABAC policies, or the full 50-app catalog.
- A matrix key per form field or per button (use the parent **feature** id; field `req`/`mode` come from FormEnvelope).
- Extra login letters for import/export (`i`, `e`) — reuse `v`/`c`/`u`/`d`.
- Kernel `if (app === "hr")` access or form code ([22](22-common-fields-forms-fk.md)).
- Sending IdP tokens to ledger/HR APIs.
- Client-side “authorization” as the only check.
- Implicit OAuth2 flow or embedding `client_secret` in Flutter/React.
- A second auth system per sold application.
- Public Nginx proxy of `/api/v1/ai/identity/bio/**` (enumeration / presentation attacks).
- Storing raw face or fingerprint images as the match key; logging embeddings; returning cosine scores to the client in prod.
- 1:N gallery search at login (v1 is **1:1** after username).
- Face/fingerprint login when Python is down (`503`, generic; do not leak).
- Sending the password (or bio image) to the license server.
- Face login from a file, gallery, `image_picker`, or a single still with no live challenge (web **or** Flutter).
- Trusting client PAD alone (skip Python PAD/match).
- Using a toy distance with no liveness / quality gate.
- Closed cloud OCR/face/fingerprint APIs in the default pack ([05](05-backend-python-design.md) §7.2, [13](13-open-source-licensing.md)).
