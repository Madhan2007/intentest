# 16 — Job and crontab wrappers (no hang, no overlap)

## 1. Split vs upgrades

Crontab **definition** is one-time (`crontab -u tsuser`, stored under `/var/spool/cron`). It calls **`/usr/local/bin/opzhub-run-cron`**, also one-time. That stub `exec`s the **current** `/home/tsuser/opzhub/infra/wrappers/run-cron.sh` so job *code* upgrades with the release.

Locks and job logs are **not** in the release tree:

| File | Path |
| ---- | ---- |
| lock | `/var/lib/opzhub/run/locks/<job>.lock` |
| pid | `/var/lib/opzhub/run/<job>.pid` |
| log | `/var/log/opzhub/jobs/<job>.log` |

Long-running **services** (`opzgui` / `opzbe` / `opzpy`) use a different tree so cron jobs cannot collide: `/var/lib/opzhub/run/svc/<id>.pid` and `/var/log/opzhub/svc/<id>.log`. Operators use `opzhubctl` ([06](06-scripts-design.md)), not crontab, to start those.

Wiping `/home/tsuser/opzhub` on upgrade does not drop locks/logs.

## 2. `run-job.sh` contract

```
/usr/local/bin/opzhub-run-job [--timeout SEC] [--job NAME] -- command [args...]
```

Same as before: `flock -n` (exit 75 if busy), `timeout --kill-after=15s`, `setsid -w`. No hang, no stacked sessions. Stale PID is reaped before lock. Log `start` / `end` / `skip` with `job=` and `rc=`; API-triggered jobs also print `correlation_id` ([24](24-hang-prevention-error-reporting.md)). Exit 124 = `hang_timeout`.

## 3. Rules

1. Crontab user is **tsuser**.
2. Crontab lines use `/usr/local/bin/opzhub-*`, not a versioned zip path.
3. `init-system.sh` (root) installs those bins once.
4. Logs trimmed in `/var/log/opzhub` only.
5. Hourly `db-backup`. Periodic `license-beat` to the central license hub ([20](20-licensing-site-central.md)).
