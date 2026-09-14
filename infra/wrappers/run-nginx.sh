#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"

opzhub_drop_root_if_requested "$@"
opzhub_assert_tsuser
opzhub_layout
opzhub_prepare_dirs
opzhub_cd_app

NGINX_BIN="${NGINX_BIN:-nginx}"
opzhub_require_cmd "${NGINX_BIN}"

CONF="${NGINX_CONF:-${OPZHUB_HOME}/gateway/nginx.conf}"
[ -r "${CONF}" ] || opzhub_die "nginx conf not readable: ${CONF}"

NGINX_PID="${OPZHUB_RUN}/nginx.pid"

exec "${NGINX_BIN}" -g "daemon off; pid ${NGINX_PID};" -c "${CONF}"
