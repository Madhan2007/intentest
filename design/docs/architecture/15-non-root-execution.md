# 15 — Non-Root Execution & Split Layout (ManageMyOpz / opzhub)

## 1. Intent

Product: **ManageMyOpz**. Short name: **opzhub**. Runtime user: **`tsuser`**.

The application tree is **loaded and replaced on every upgrade**. Putting one-time OS/machine files inside that tree is wrong: the next extract would wipe secrets, site config, data, logs, and host identity.

Split is mandatory:

| Class | Lives where | Owner | On upgrade |
| ----- | ----------- | ----- | ---------- |
| **Release (code)** | `/home/tsuser/opzhub/` | `tsuser` | **Replaced** |
| **Site / one-time** | `/etc/opzhub/`, `/usr/local/bin/opzhub-*` | `root:tsuser` | **Kept** |
| **Persistent state** | `/var/lib/opzhub/`, Docker volumes | `tsuser` (data) / Docker | **Kept** |
| **Logs** | `/var/log/opzhub/` | `tsuser` | **Kept** (rotated) |

Root (or a bootstrap as root) installs **system** files once. **`tsuser` never runs the JVM/Python/Nginx as root**, but **may read** `/etc/opzhub`.

## 2. Identity

| Field | Value |
| ----- | ----- |
| Product | ManageMyOpz |
| Short name | opzhub |
| Runtime user | **tsuser** (2100:2100) |
| tsuser home | `/home/tsuser` |
| **Release root** | `/home/tsuser/opzhub` |
| **Site config** | `/etc/opzhub` |
| **State** | `/var/lib/opzhub` |
| **Logs** | `/var/log/opzhub` |
| System stubs | `/usr/local/bin/opzhub-run-cron` (and siblings) |

## 3. What is one-time (do not ship inside the upgrade tarball)

Created by **`init-system.sh` as root**, then left alone:

```
/etc/opzhub/                     root:tsuser  0750
  opzhub.env                     root:tsuser  0640   path exports
  platform.override.yaml         root:tsuser  0640   this machine
  secrets.env                    root:tsuser  0640   never in git / never in release zip
  certs/public/                  root:tsuser  0750   Let's Encrypt / public TLS
  certs/internal/                root:tsuser  0750   internal CA (rotate, don't delete on app upgrade)

/usr/local/bin/opzhub-run-cron   root:root    0755   stable crontab entrypoint
/usr/local/bin/opzhub-run-job    root:root    0755
/usr/local/bin/opzhubctl         root:root    0755   exec's current release CLI

/var/lib/opzhub/                 tsuser:tsuser 0750
  data/  staging/  web-cache/  run/locks/  run/svc/  tmp/  backup/db/

/var/log/opzhub/                 tsuser:tsuser 0750
  jobs/  svc/

/var/spool/cron/crontabs/tsuser  root:tsuser  0600   crontab body (one-time install)
```

Docker named volumes (`pg-data`, `valkey-data`) stay in the engine's volume dir (typically under `/var/lib/docker`). They are **not** copied into `/home/tsuser/opzhub`.

Systemd units (if used later): `/etc/systemd/system/opzhub*.service` — root, one-time.

## 4. What is the application (replaced every upgrade)

```
/home/tsuser/opzhub/             tsuser:tsuser  0750
  common/  modules/  gateway/  infra/wrappers/
  opzhub-be-app.jar  (when built)
  ...
```

Upgrade = extract new files **here only**. Do **not** rsync `--delete` onto `/etc/opzhub` or `/var/lib/opzhub`.

`PLATFORM_CONFIG_PATH` reads **`/etc/opzhub/platform.override.yaml`** merged over defaults that ship in the release (`/home/tsuser/opzhub/platform/config/platform.yaml`).

## 5. Runtime processes

Still **tsuser** (2100) for: core engine, AI, worker, SPA, Nginx in Compose, `opzhubctl`, cron jobs.

Root is allowed only for: first `init-system.sh`, `apt`/`docker` install, writing `/etc/opzhub`, installing crontab/systemd.

## 6. Wrappers

| Script | When | Who |
| ------ | ---- | --- |
| `init-system.sh` | **Once** per host | **root** |
| `init-home.sh` | After each upgrade (or first app extract) | **tsuser** (or root then drop) |
| `run-opzhub-*.sh` | Every start (Compose entrypoint or host pid) | **tsuser** |
| `run-opzhubctl.sh` | `start` / `stop` / `restart` / `status` / `logs` / `release` | **tsuser** |
| `/usr/local/bin/opzhubctl` | operator CLI | **tsuser**; execs current `run-opzhubctl.sh` |
| `/usr/local/bin/opzhub-run-cron` | crontab | **tsuser**; execs current `/home/tsuser/opzhub/infra/wrappers/run-cron.sh` |

## 7. Compose mounts (illustrative)

```yaml
services:
  opzhub-be-app:
    user: "2100:2100"
    working_dir: /home/tsuser/opzhub
    volumes:
      - /home/tsuser/opzhub:/home/tsuser/opzhub:ro   # release
      - /etc/opzhub:/etc/opzhub:ro                   # site, one-time
      - /var/lib/opzhub:/var/lib/opzhub              # state
      - /var/log/opzhub:/var/log/opzhub              # logs
    entrypoint: ["bash", "/home/tsuser/opzhub/infra/wrappers/run-opzhub-engine.sh"]
```

`opzhub.env` sets `OPZHUB_CONFIG=/etc/opzhub`, `OPZHUB_DATA=/var/lib/opzhub`, `OPZHUB_LOGS=/var/log/opzhub`, `OPZHUB_HOME=/home/tsuser/opzhub`.

## 8. Creating tsuser + system dirs (once)

```bash
# as root — init-system.sh
groupadd --gid 2100 tsuser
useradd --system --uid 2100 --gid 2100 --create-home --home-dir /home/tsuser \
  --shell /usr/sbin/nologin tsuser
usermod -aG docker tsuser
```

Do **not** store site secrets under `/home/tsuser/opzhub` (that directory is the upgrade target).

Jobs/cron: [16](16-jobs-crontab.md).
