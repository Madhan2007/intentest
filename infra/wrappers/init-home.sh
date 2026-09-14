#!/usr/bin/env bash
# After each application extract/upgrade. Only touches /home/tsuser/opzhub.
# Does not modify /etc/opzhub, /var/lib/opzhub, or secrets.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

umask 027

if [ -f /etc/opzhub/opzhub.env ]; then
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  . /etc/opzhub/opzhub.env
  set +a
fi

HOME_TS="${HOME:-/home/tsuser}"
APP="${OPZHUB_HOME:-${HOME_TS}/opzhub}"

if [ "$(id -u)" -eq 0 ]; then
  id tsuser >/dev/null 2>&1 || {
    printf '%s\n' "init-home: run init-system.sh first (tsuser missing)" >&2
    exit 77
  }
fi

mkdir -p "${APP}/infra/wrappers" "${APP}/tmp"

chmod 0750 "${APP}"

if [ "$(id -u)" -eq 0 ]; then
  chown -R 2100:2100 "${APP}"
fi

printf '%s\n' "init-home: release tree ready at ${APP} (site files in /etc/opzhub and /var left untouched)"
exit 0
