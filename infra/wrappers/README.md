# ManageMyOpz (opzhub) wrappers

**tsuser** runs the app. **root** installs one-time site files.

| Script | Role |
| ------ | ---- |
| `init-system.sh` | **Once, as root:** `/etc/opzhub`, `/var/lib/opzhub`, `/var/log/opzhub`, `tsuser`, `/usr/local/bin/opzhub-*` |
| `init-home.sh` | After each **upgrade:** only `/home/tsuser/opzhub` |
| `run-opzhub-engine.sh` | Core engine :8114 |
| `run-opzhub-be-core.sh` | Combined Python API and worker |
| `run-web.sh` / `run-nginx.sh` | HTTP |
| `run-opzhubctl.sh` | **CLI:** start / stop / restart / status / logs / release (`opzhubctl`) |
| `run-job.sh` / `run-script.sh` | lock + timeout |
| `run-cron.sh` | cron dispatcher (health, log-trim, **db-backup** hourly) |
| `system/opzhub-*` | copied to `/usr/local/bin` (stable) |

Upgrade replaces **`/home/tsuser/opzhub` only**. Never extract over `/etc/opzhub` or `/var/lib/opzhub`.

```
# as tsuser (uid 2100)
opzhubctl start opzgui opzbe opzpy
opzhubctl status
opzhubctl release
```

