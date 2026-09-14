#!/usr/bin/env bash
# Crontab entry: always go through run-job.sh (lock + timeout, no stacked sessions).
# Example crontab (user tsuser, never root):
#   */5 * * * * /home/tsuser/opzhub/infra/wrappers/run-cron.sh health
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JOB="${1:?job name: health | log-trim | reap-stale | doctor-lite | db-backup | license-beat}"
shift || true

case "${JOB}" in
  health)
    exec "${ROOT}/run-job.sh" --timeout 60 --job health -- "${ROOT}/jobs/health.sh" "$@"
    ;;
  log-trim)
    exec "${ROOT}/run-job.sh" --timeout 120 --job log-trim -- "${ROOT}/jobs/log-trim.sh" "$@"
    ;;
  reap-stale)
    exec "${ROOT}/run-job.sh" --timeout 30 --job reap-stale -- "${ROOT}/jobs/reap-stale.sh" "$@"
    ;;
  doctor-lite)
    exec "${ROOT}/run-job.sh" --timeout 90 --job doctor-lite -- "${ROOT}/jobs/doctor-lite.sh" "$@"
    ;;
  db-backup)
    exec "${ROOT}/run-job.sh" --timeout 3600 --job db-backup -- "${ROOT}/jobs/db-backup.sh" "$@"
    ;;
  license-beat)
    exec "${ROOT}/run-job.sh" --timeout 30 --job license-beat -- "${ROOT}/jobs/license-beat.sh" "$@"
    ;;
  *)
    printf '%s\n' "unknown cron job: ${JOB}" >&2
    exit 64
    ;;
esac
