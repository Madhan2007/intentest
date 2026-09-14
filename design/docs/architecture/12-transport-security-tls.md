# 12 — Transport Security (HTTPS / TLS on every hop)

## 1. Requirement

**All application and service communications are encrypted.** Plain HTTP, plain WS, unencrypted Postgres, and unencrypted Valkey are **forbidden in `solution.profile: prod`**. `dev` may relax only behind `security.tls.allow_insecure_dev: true` (never the default). Kafka TLS rules apply only if Kafka is added in a later drop ([11](11-broker-selection.md)).

This includes:

| Path | Protocol |
| ---- | -------- |
| Browser → Nginx | HTTPS, WSS (TLS 1.2+) |
| Flutter app → Nginx | HTTPS, WSS (TLS 1.2+); prod builds pin the public cert/SPKI |
| Nginx → ERP / AI / SPA | HTTPS / WSS (internal certificates) |
| ERP ↔ AI (job result HTTP) | HTTPS |
| Scripts → ERP / AI | HTTPS |
| Java/Python → PostgreSQL | TLS (`ssl_mode: verify-full` in prod) |
| Java/Python → Valkey (cache + broker streams + Pub/Sub) | TLS |
| `opzhubctl` to any of the above | same TLS settings as the kernel |

SSH to EC2 is orthogonal (host admin). It is not a substitute for application TLS.

## 2. Trust model

```
                         public CA or ACM
                    ┌─────────── HTTPS/WSS ───────────┐
                    │                                 │
                 Browser                           Nginx :443
                    │                                 │
                    │                    internal CA (erp-internal)
                    │         ┌───────────────────────┼───────────────────────┐
                    │         ▼                       ▼                       ▼
                    │   opzhub-be-app:8114          opzhub-be-core:8117      opzhub-web-app:8109
                    │         │                       │
                    │         └──────── HTTPS ────────┘
                    │
                    │   postgres:5432 TLS    valkey:6380 TLS
                    └─────────────────────────────────────────────────────────
```

- **Public cert:** Let’s Encrypt, ACM, or customer PEM on Nginx. Browsers trust a public CA. Flutter **prod** flavors pin that cert (or SPKI); **dev** flavor may trust mkcert.
- **Internal CA:** `opzhubctl certs issue` writes an internal CA + per-service server certs + optional client certs into a Docker secret/volume (`certs-internal`). Apps trust **only** that CA (`verify-full` / `ssl.truststore`).
- **mTLS (prod default for service-to-service HTTP):** Nginx and sidecars present client certificates. Unauthenticated internal calls are rejected.

## 3. YAML contract

```yaml
security:
  tls:
    mode: required                 # required | optional (optional only if profile!=prod)
    min_version: "1.2"
    allow_insecure_dev: false
    public:
      listen_https: 443            # HTTPS only — nginx has no HTTP listener
      hsts_seconds: 31536000
      cert_path: /certs/public/fullchain.pem
      key_path: /certs/public/privkey.pem
    internal:
      ca_path: /certs/internal/ca.crt
      mtls: true                   # prod: true
      erp_url: https://opzhub-be-app:8114
      ai_url: https://opzhub-be-core:8117
      web_url: https://opzhub-web-app:8109
    postgres:
      ssl_mode: verify-full        # mirrors db.postgres.ssl_mode
    valkey:
      tls: true
      port: 6380
```

`http.opzhub-be-app.internal_url` and `http.opzhub-be-core.internal_url` **must** be `https://` when `security.tls.mode: required`. `opzhubctl doctor` fails the solution if they are `http://` in prod.

## 4. Public edge (Nginx)

- `listen 8102 ssl;` with modern ciphers; disable TLS 1.0/1.1. Host publishes 443→8102. No HTTP listener and no redirect — nginx serves HTTPS only.
- WebSocket location uses **WSS** from the browser (`wss://host/ws/opzhub`). Nginx proxies to upstream HTTPS/WSS (`proxy_pass https://opzhub_engine`).
- `Strict-Transport-Security` when public TLS is on.
- Cookies: `Secure`, `HttpOnly`, `SameSite=Lax` (or `Strict` if the SPA never cross-sites).
- `client_max_body_size` unchanged; encryption does not replace upload limits.

Illustrative differences vs the earlier HTTP-only sketch:

```nginx
upstream opzhub_engine { server opzhub-be-app:8114; }

server {
  listen 8102 ssl;
  http2 on;
  ssl_certificate     /certs/public/fullchain.pem;
  ssl_certificate_key /certs/public/privkey.pem;
  ssl_protocols       TLSv1.2 TLSv1.3;

  location /ws/opzhub {
    proxy_pass https://opzhub_engine;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_ssl_trusted_certificate /certs/internal/ca.crt;
    proxy_ssl_verify on;
    proxy_ssl_name opzhub-be-app;
    # timeouts as in doc 09
  }

  location /api/v1/opzhub/ {
    proxy_pass https://opzhub_engine;
    proxy_ssl_trusted_certificate /certs/internal/ca.crt;
    proxy_ssl_verify on;
    proxy_ssl_name opzhub-be-app;
  }
}
```

Frontend public config advertises `wss` and `https` paths (same-origin `/api` and `/ws` so the page origin is HTTPS).

## 5. Application servers

- Spring Boot binds **8114**, FastAPI **8117**, SPA **8109** with the internal server cert (or a sidecar; one pattern per implementation, not mixed). Do **not** use 8443, 8080, or 8000.
- Health checks from Compose use `https://127.0.0.1:8114/...` (ERP) and `https://127.0.0.1:8117/...` (AI) with the CA mounted, or a localhost-only HTTP **health** port bound to `127.0.0.1` inside the container (not published). Prefer HTTPS health with the CA.
- Java inbound OCR result API is `https://opzhub-be-app:8114/api/v1/opzhub/ocr/results` with mTLS.
- Java → FastAPI bio verify is `https://opzhub-be-core:8117/api/v1/ai/identity/bio/*` with mTLS. That path is not on public Nginx.

## 6. Data stores and brokers

| Store | Prod TLS |
| ----- | -------- |
| PostgreSQL | `ssl=on`, clients `verify-full`, server cert SAN `postgres` |
| Valkey | `tls-port 6380`, `port 0` (disable plaintext) in prod |

Passwords remain required **in addition to** TLS. TLS is not authentication by itself unless mTLS is used (HTTP services). Valkey AUTH + TLS together.

## 7. Certificate lifecycle

- Internal CA validity: long-lived CA, short-lived leaf certs (e.g. 90 days).
- `opzhubctl certs rotate` + Compose restart of affected services.
- Public certs: certbot or ACM; Nginx reload only.
- Private keys: mode `0600`, never in git, never in frontend bundles.

## 8. What `opzhubctl doctor` rejects in prod

- `security.tls.mode` not `required`
- `allow_insecure_dev: true`
- `db.postgres.ssl_mode: disable` or `allow`
- `cache.valkey` without `tls: true`
- `broker.type: kafka` (not implemented this drop — [11](11-broker-selection.md))
- Internal URLs using `http://`
- Browser WS path documented as `ws://`

## 9. Dev exception (explicit)

Local engineers may set `profile: dev` and `security.tls.allow_insecure_dev: true` to use HTTP on the bridge network. That overlay **cannot** be merged into a prod solution pack. CI for customer images runs doctor with `profile: prod`.
