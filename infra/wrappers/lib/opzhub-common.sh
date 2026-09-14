# Shared helpers for ManageMyOpz (opzhub) non-root wrappers.
# SPDX-License-Identifier: Apache-2.0
# shellcheck shell=bash

: "${OPZHUB_APP_NAME:=managemyopz}"
: "${OPZHUB_SHORT:=opzhub}"
: "${OPZHUB_APP_USER:=tsuser}"
: "${OPZHUB_APP_UID:=2100}"
: "${OPZHUB_APP_GID:=2100}"
: "${HOME:=/home/tsuser}"
: "${OPZHUB_HOME:=${HOME}/${OPZHUB_SHORT}}"

umask 027

opzhub_die() {
  printf '%s\n' "opzhub-wrapper: $*" >&2
  exit 77
}

opzhub_require_cmd() {
  command -v "$1" >/dev/null 2>&1 || opzhub_die "missing command: $1"
}

opzhub_is_root() {
  [ "$(id -u)" -eq 0 ]
}

# Re-exec as tsuser when started as root. Only if OPZHUB_DROP_ROOT=1.
opzhub_drop_root_if_requested() {
  if ! opzhub_is_root; then
    return 0
  fi
  if [ "${OPZHUB_DROP_ROOT:-0}" != "1" ]; then
    opzhub_die "refusing to run as root (uid 0). start as tsuser or set OPZHUB_DROP_ROOT=1 to drop and exec"
  fi
  [ -n "${OPZHUB_APP_UID:-}" ] || opzhub_die "OPZHUB_APP_UID is required to drop root"
  [ -n "${OPZHUB_APP_GID:-}" ] || opzhub_die "OPZHUB_APP_GID is required to drop root"

  if command -v setpriv >/dev/null 2>&1; then
    exec setpriv --reuid="${OPZHUB_APP_UID}" --regid="${OPZHUB_APP_GID}" --clear-groups --inh-caps=-all -- "$0" "$@"
  fi
  if command -v su-exec >/dev/null 2>&1; then
    exec su-exec "${OPZHUB_APP_UID}:${OPZHUB_APP_GID}" "$0" "$@"
  fi
  if command -v gosu >/dev/null 2>&1; then
    exec gosu "${OPZHUB_APP_UID}:${OPZHUB_APP_GID}" "$0" "$@"
  fi
  opzhub_die "cannot drop root: install setpriv (util-linux), su-exec, or gosu"
}

opzhub_assert_not_root() {
  if opzhub_is_root; then
    opzhub_die "still running as root after drop; aborting"
  fi
}

opzhub_assert_uid() {
  local expected="$1"
  local actual
  actual="$(id -u)"
  [ "${actual}" = "${expected}" ] || opzhub_die "expected uid ${expected}, got ${actual} (tsuser must be 2100)"
}

opzhub_assert_tsuser() {
  opzhub_assert_not_root
  opzhub_assert_uid "${OPZHUB_APP_UID}"
  local name
  name="$(id -un)"
  if [ "${name}" != "${OPZHUB_APP_USER}" ] && [ "${name}" != "tsuser" ]; then
    opzhub_die "expected user tsuser (uid ${OPZHUB_APP_UID}), got ${name}"
  fi
}

opzhub_layout() {
  if [ -f /etc/opzhub/opzhub.env ]; then
    set -a
    # shellcheck disable=SC1091
    . /etc/opzhub/opzhub.env
    set +a
  fi
  export HOME="${HOME:-/home/tsuser}"
  export OPZHUB_HOME="${OPZHUB_HOME:-${HOME}/${OPZHUB_SHORT}}"
  # Site (one-time) vs release (upgraded)
  export OPZHUB_CONFIG="${OPZHUB_CONFIG:-/etc/opzhub}"
  export OPZHUB_DATA="${OPZHUB_DATA:-/var/lib/opzhub}"
  export OPZHUB_LOGS="${OPZHUB_LOGS:-/var/log/opzhub}"
  export OPZHUB_RUN="${OPZHUB_RUN:-${OPZHUB_DATA}/run}"
  export OPZHUB_LOCKS="${OPZHUB_LOCKS:-${OPZHUB_RUN}/locks}"
  export OPZHUB_TMP="${OPZHUB_TMP:-${OPZHUB_DATA}/tmp}"
  export OPZHUB_CERTS="${OPZHUB_CERTS:-${OPZHUB_CONFIG}/certs}"
  export XDG_CACHE_HOME="${OPZHUB_DATA}/.cache"
  export XDG_CONFIG_HOME="${OPZHUB_CONFIG}"
  export TMPDIR="${OPZHUB_TMP}"
  export PLATFORM_CONFIG_PATH="${PLATFORM_CONFIG_PATH:-${OPZHUB_CONFIG}/platform.override.yaml}"
}

opzhub_prepare_dirs() {
  opzhub_layout
  local d
  for d in "${OPZHUB_CONFIG}" "${OPZHUB_CERTS}"; do
    [ -d "${d}" ] || opzhub_die "missing site dir (run init-system.sh as root): ${d}"
    [ -r "${d}" ] || opzhub_die "site dir not readable by $(id -un): ${d}"
  done
  for d in "${OPZHUB_HOME}" "${OPZHUB_DATA}" "${OPZHUB_LOGS}" \
           "${OPZHUB_RUN}" "${OPZHUB_LOCKS}" "${OPZHUB_TMP}" \
           "${OPZHUB_RUN}/svc" \
           "${OPZHUB_DATA}/data" "${OPZHUB_DATA}/data/staging" "${OPZHUB_DATA}/data/web-cache" \
           "${OPZHUB_DATA}/backup" "${OPZHUB_DATA}/backup/db" \
           "${OPZHUB_LOGS}/jobs" "${OPZHUB_LOGS}/svc" "$@"; do
    [ -d "${d}" ] || opzhub_die "missing state dir (run init-system.sh): ${d}"
    [ -w "${d}" ] || opzhub_die "state dir not writable by $(id -un): ${d}"
  done
}

opzhub_cd_app() {
  opzhub_layout
  [ -d "${OPZHUB_HOME}" ] || opzhub_die "OPZHUB_HOME not a directory: ${OPZHUB_HOME}"
  cd "${OPZHUB_HOME}"
}

# Compatibility aliases used by older snippet names in docs
erp_die() { opzhub_die "$@"; }
erp_is_root() { opzhub_is_root; }
erp_assert_tsuser() { opzhub_assert_tsuser; }
erp_drop_root_if_requested() { opzhub_drop_root_if_requested "$@"; }
