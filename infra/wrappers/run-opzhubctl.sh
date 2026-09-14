#!/usr/bin/env bash
# ManageMyOpz operator CLI (opzhubctl).
# Process verbs (start/stop/restart/status/logs/release) live here so they
# work before the Python kernel CLI exists. migrate/doctor/… pass through.
# Never root. Long jobs use run-job.sh.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
# shellcheck source=lib/opzhub-ctl.sh
. "${ROOT}/lib/opzhub-ctl.sh"

if opzhub_is_root; then
  opzhub_die "opzhubctl must not run as root; use tsuser (uid 2100)"
fi
opzhub_assert_tsuser
opzhub_layout
opzhub_cd_app

export OPZHUB_WRAP="${ROOT}"

need_state() {
  opzhub_prepare_dirs
}

usage() {
  cat <<'EOF'
opzhubctl — ManageMyOpz operator CLI

Process control:
  start [all|NAME...]     start services (default: all present)
  stop  [all|NAME...]     stop
  restart [all|NAME...]   restart
  status [all|NAME...]    running / stopped (exit 3 if any requested is down)
  logs NAME [-f] [-n N]   tail service log
  release                 version, site paths, modules, process state
  version                 short version line
  list                    service names

Schema / recovery (kernel CLI when installed; also cron):
  migrate                 create/update schema (no data wipe)
  backup                  manual dump (hourly cron: db-backup)
  restore --file PATH     recovery

Kernel commands (Python, when installed):
  health | migrate | doctor | module-gen | compose | backup | restore | …

Names: opzgw  opzgui  opzbe  opzpy
EOF
}

opzhub_try_migrate() {
  [ "${OPZHUB_MIGRATE_ON_START:-1}" = "1" ] || return 0
  local pyctl="${OPZHUBCTL_BIN:-${OPZHUB_HOME}/common/scripts/opzhubctl}"
  if [ -x "${pyctl}" ]; then
    "${pyctl}" migrate
    return
  fi
  local python_bin="${PYTHON_BIN:-python3}"
  if command -v "${python_bin}" >/dev/null 2>&1 && \
     "${python_bin}" -c "import opzhub_scripts.cli" >/dev/null 2>&1; then
    "${python_bin}" -m opzhub_scripts.cli migrate
    return
  fi
  return 0
}

cmd_kernel() {
  local pyctl="${OPZHUBCTL_BIN:-${OPZHUB_HOME}/common/scripts/opzhubctl}"
  if [ -x "${pyctl}" ]; then
    exec "${pyctl}" "$@"
  fi
  local python_bin="${PYTHON_BIN:-python3}"
  if command -v "${python_bin}" >/dev/null 2>&1 && \
     "${python_bin}" -c "import opzhub_scripts.cli" >/dev/null 2>&1; then
    exec "${python_bin}" -m opzhub_scripts.cli "$@"
  fi
  printf '%s\n' "opzhubctl: kernel command '$*' needs common/scripts (not installed yet)." >&2
  printf '%s\n' "Process verbs work now: start | stop | restart | status | logs | release" >&2
  exit 5
}

cmd_lifecycle() {
  local verb="$1"
  shift
  local stack=0 ids id rc=0 resolved
  if [ $# -eq 0 ]; then
    stack=1
  elif [ $# -eq 1 ]; then
    resolved="$(opzhub_ctl_resolve "$1" 2>/dev/null || true)"
    if [ "${resolved}" = all ]; then
      stack=1
    fi
  fi
  ids="$(opzhub_ctl_expand_targets "$@")" || exit 5
  case "${verb}" in
    start)
      if ! opzhub_try_migrate; then
        exit 4
      fi
      if [ "${stack}" -eq 1 ]; then
        opzhub_ctl_start_stack || rc=1
      else
        while IFS= read -r id; do
          [ -n "${id}" ] || continue
          opzhub_ctl_start_one "${id}" || rc=1
        done <<EOF
${ids}
EOF
      fi
      ;;
    stop)
      if [ "${stack}" -eq 1 ]; then
        opzhub_ctl_stop_stack || rc=1
      else
        while IFS= read -r id; do
          [ -n "${id}" ] || continue
          opzhub_ctl_stop_one "${id}" || rc=1
        done <<EOF
${ids}
EOF
      fi
      ;;
    restart)
      if ! opzhub_try_migrate; then
        exit 4
      fi
      if [ "${stack}" -eq 1 ]; then
        opzhub_ctl_restart_stack || rc=1
      else
        while IFS= read -r id; do
          [ -n "${id}" ] || continue
          opzhub_ctl_restart_one "${id}" || rc=1
        done <<EOF
${ids}
EOF
      fi
      ;;
    status)
      printf '%-8s %-10s %s\n' ID STATE DETAIL
      printf 'runtime=%s\n' "$(opzhub_ctl_runtime)"
      while IFS= read -r id; do
        [ -n "${id}" ] || continue
        if ! opzhub_ctl_status_one "${id}"; then
          rc=3
        fi
      done <<EOF
${ids}
EOF
      ;;
  esac
  exit "${rc}"
}

if [ $# -eq 0 ]; then
  usage
  exit 0
fi

case "$1" in
  -h|--help|help)
    usage
    exit 0
    ;;
  list)
    opzhub_ctl_list
    exit 0
    ;;
  start|stop|restart|status)
    need_state
    cmd_lifecycle "$@"
    ;;
  logs)
    need_state
    shift
    opzhub_ctl_logs "$@"
    exit $?
    ;;
  release|info)
    need_state
    opzhub_ctl_release
    exit 0
    ;;
  version)
    rel="${OPZHUB_HOME}/RELEASE"
    [ -f "${rel}" ] || rel="${OPZHUB_HOME}/platform/RELEASE"
    ver="$(opzhub_ctl_kv "${rel}" VERSION)"
    printf '%s %s\n' "${OPZHUB_SHORT:-opzhub}" "${ver:-unknown}"
    exit 0
    ;;
  health|migrate|doctor|module-gen|compose|certs|backup|restore)
    cmd_kernel "$@"
    ;;
  *)
    cmd_kernel "$@"
    ;;
esac
