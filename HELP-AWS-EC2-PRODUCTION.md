# AWS EC2 Production Help

Deploy ManageMyOpz on a dedicated Ubuntu 24.04 EC2 instance using Docker
Compose. This runbook uses host bind mounts for persistent configuration, data,
logs, and real TLS certificate material.

## 1. Prerequisites

- Ubuntu 24.04 LTS EC2 instance
- A DNS name pointing to the instance public address
- A real TLS certificate for that DNS name
- Security group inbound rules:
  - TCP `22` restricted to the administrator CIDR
  - TCP `443` for public HTTPS (nginx has no HTTP listener, so there is no port-80 redirect to allow)
- Do **not** expose `5432`, `6379`, `8114`, `8117`, `8109`, or `8124`.

## 2. Bootstrap the host

Copy this repository to the instance first, then run the bootstrap script as
root. It installs Docker, creates the non-login `tsuser` account, and creates
persistent host directories. It does not start the application.

```bash
sudo bash infra/ec2-bootstrap.sh
```

The persistent paths are:

| Path | Purpose |
| --- | --- |
| `/etc/opzhub` | Configuration and public certificates |
| `/var/lib/opzhub` | Database/cache/application state and backups |
| `/var/log/opzhub` | Runtime, service, and job logs |
| `/home/tsuser/opzhub` | Replaceable application release tree |

## 3. Ship the release

From the build machine, copy the product repository without local artifacts or
secrets:

```bash
rsync -az --delete \
  --exclude .git \
  --exclude node_modules \
  --exclude target \
  --exclude .dart_tool \
  --exclude .env \
  --exclude infra/.env \
  ./ ec2-user@<host>:/home/tsuser/opzhub/
```

On the EC2 instance:

```bash
sudo chown -R tsuser:tsuser /home/tsuser/opzhub
sudo -u tsuser bash /home/tsuser/opzhub/infra/wrappers/init-home.sh
```

## 4. Configure production secrets and storage

As `tsuser`, create the ignored production environment file:

```bash
sudo -u tsuser -H bash
cd /home/tsuser/opzhub/infra
cp .env.example .env
chmod 600 .env
vi .env
```

Set all of the following values with unique, high-entropy secrets:

```dotenv
POSTGRES_PASSWORD=<unique-strong-secret>
VALKEY_PASSWORD=<different-strong-secret>
OPZHUB_HOST_HTTP=80
OPZHUB_HOST_HTTPS=443
OPZHUB_ETC_SRC=/etc/opzhub
OPZHUB_VARLIB_SRC=/var/lib/opzhub
OPZHUB_VARLOG_SRC=/var/log/opzhub
```

Never commit `.env`, paste its contents into tickets, or reuse development
passwords.

## 5. Install the public TLS certificate

Before starting Compose, install the certificate and private key:

```bash
sudo install -o tsuser -g tsuser -m 0640 fullchain.pem \
  /etc/opzhub/certs/public/fullchain.pem
sudo install -o tsuser -g tsuser -m 0640 privkey.pem \
  /etc/opzhub/certs/public/privkey.pem
```

The `certs` Compose task generates a self-signed certificate only when these
files are absent. A real certificate installed first is retained.

## 6. Validate and start

Run these commands as `tsuser` from the infrastructure directory:

```bash
cd /home/tsuser/opzhub/infra

docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml build --progress plain
docker compose -f docker-compose.yml up -d
```

The production services are `postgres`, `valkey`, `opzhub-be-app`,
`opzhub-web-app`, and `opzhub-ui-service`. `opzhub-be-core` is the optional
combined Python API and worker runtime; `ai`, `ocr`, or `image` starts it.

Verify from the host and an external network:

```bash
# Host status
docker compose -f docker-compose.yml ps

# Local public health
curl -f https://localhost/healthz

# External public health
curl -f https://<your-domain>/api/v1/opzhub/health
```

Run configuration validation:

```bash
cd /home/tsuser/opzhub/common/scripts
python opzhubctl doctor --as-prod
```

## First login

On the first PostgreSQL deployment, `opzhub-be-app` creates a single
`admin@technosprint.net` account only when no user records exist. It generates a random password, stores
only the Argon2id hash in PostgreSQL, and writes the one-time password to:

```bash
sudo cat /etc/opzhub/bootstrap-admin-password
```

Log in as `admin@technosprint.net` with that value, then change it through the
identity management flow when available. If users already exist, bootstrap does
not create a new account; an existing username `admin` is renamed to
`admin@technosprint.net` when that email is not already taken. The password is
not changed.

For a deployment-controlled first password, set
`OPZHUB_BOOTSTRAP_ADMIN_PASSWORD` in the protected `infra/.env` before the
first startup. Do not place that value in source control or deployment logs.

## 7. Operations

```bash
# All Compose logs
docker compose -f /home/tsuser/opzhub/infra/docker-compose.yml logs -f

# Specific application service
docker compose -f /home/tsuser/opzhub/infra/docker-compose.yml logs -f opzhub-be-app

# Lifecycle and status via host CLI
opzhubctl status
opzhubctl logs opzbe -f
opzhubctl restart opzgw
opzhubctl release
```

Use `Ctrl+C` only to stop log following; it does not stop containers.

## 8. Upgrade

1. Back up PostgreSQL before changing the release.
2. Ship the new release tree to `/home/tsuser/opzhub`.
3. Preserve `/etc/opzhub`, `/var/lib/opzhub`, and `/var/log/opzhub`.
4. Run the following as `tsuser`:

```bash
cd /home/tsuser/opzhub/infra
docker compose -f docker-compose.yml build --progress plain
docker compose -f docker-compose.yml up -d
opzhubctl migrate
opzhubctl health --wait 120
```

Do not run `docker compose down -v` in production: it removes persistent
volumes when named volumes are in use.

## Production security checklist

- Use a real CA-issued certificate; never expose the self-signed development
  certificate publicly.
- Keep `.env` mode `0600` and owned by `tsuser`.
- Use distinct PostgreSQL and Valkey passwords.
- Restrict SSH and use key-based access.
- Maintain encrypted EBS volumes and tested database backups.
- Run `opzhubctl doctor --as-prod` before every deployment.
- Internal mTLS is a documented follow-up; current internal Docker traffic is
  plaintext but isolated on `opzhub-internal`.
