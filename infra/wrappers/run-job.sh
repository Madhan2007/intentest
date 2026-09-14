#!/usr/bin/env bash
# Run a ManageMyOpz job with exclusive lock + hard timeout.
# Prevents overlapping crontab/script sessions and hung leftovers.
#
# Usage:
#   run-job.sh [--timeout SEC] [--job NAME] -- command [args...]
#
# Exit 75  = skipped (already running) — crontab should treat as success/skip, not pile up
# Exit 77  = refused (root / bad user / missing lock dir)
# Exit 124 = timeout (GNU timeout); process group is then SIGKILLed
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"

opzhub_drop_root_if_requested "$@"
opzhub_assert_tsuser
opzhub_layout
opzhub_prepare_dirs

TIMEOUT_SEC="${OPZHUB_JOB_TIMEOUT:-300}"
JOB_NAME=""
WAIT_LOCK=0

usage() {
  printf '%s\n' "usage: run-job.sh [--timeout SEC] [--job NAME] [--wait-lock] -- command [args...]" >&2
  exit 64
}

while [ $# -gt 0 ]; do
  case "$1" in
    --timeout)
      TIMEOUT_SEC="${2:?}"
      shift 2
      ;;
    --job)
      JOB_NAME="${2:?}"
      shift 2
      ;;
    --wait-lock)
      # Off by default: waiting would hang crontab. Explicit opt-in only.
      WAIT_LOCK=1
      shift
      ;;
    --)
      shift
      break
      ;;
    -h|--help)
      usage
      ;;
    *)
      break
      ;;
  esac
done

[ $# -ge 1 ] || usage

if [ -z "${JOB_NAME}" ]; then
  JOB_NAME="$(basename "$1" .sh)"
fi

# Safe lock file name
JOB_NAME="$(printf '%s' "${JOB_NAME}" | tr -c 'A-Za-z0-9._-' '_')"
LOCK_FILE="${OPZHUB_LOCKS}/${JOB_NAME}.lock"
PID_FILE="${OPZHUB_RUN}/${JOB_NAME}.pid"
LOG_FILE="${OPZHUB_LOGS}/jobs/${JOB_NAME}.log"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "${OPZHUB_LOCKS}" "${OPZHUB_LOGS}/jobs" "${OPZHUB_RUN}"

reap_stale_lock() {
  if [ -f "${PID_FILE}" ]; then
    local old
    old="$(cat "${PID_FILE}" 2>/dev/null || true)"
    if [ -n "${old}" ] && ! kill -0 "${old}" 2>/dev/null; then
      rm -f "${PID_FILE}" "${LOCK_FILE}"
      printf '%s stale pid %s reaped\n' "${STAMP}" "${old}" >>"${LOG_FILE}"
    fi
  fi
}

reap_stale_lock

FLOCK_OPTS="-n"
if [ "${WAIT_LOCK}" -eq 1 ]; then
  FLOCK_OPTS="-w 2"
fi

opzhub_require_cmd flock
opzhub_require_cmd timeout

# Open lock on FD 9; never block crontab (default -n).
exec 9>"${LOCK_FILE}"
if ! flock ${FLOCK_OPTS} 9; then
  printf '%s skip job=%s already running (no hang)\n' "${STAMP}" "${JOB_NAME}" >>"${LOG_FILE}"
  exit 75
fi

echo $$ >"${PID_FILE}"
cleanup() {
  rm -f "${PID_FILE}"
}
trap cleanup EXIT INT TERM

{
  printf '%s start job=%s timeout=%ss cmd=%s\n' "${STAMP}" "${JOB_NAME}" "${TIMEOUT_SEC}" "$*"
  # New session so children die with the timeout; --kill-after stops hangs after TERM.
  # --foreground keeps signals in this wrapper (crontab-friendly).
  set +e
  timeout --foreground --kill-after=15s "${TIMEOUT_SEC}s" setsid -w "$@"
  rc=$?
  set -e
  printf '%s end job=%s rc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${JOB_NAME}" "${rc}"
  exit "${rc}"
} >>"${LOG_FILE}" 2>&1
