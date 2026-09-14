#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"

export OPZHUB_DROP_ROOT="${OPZHUB_DROP_ROOT:-0}"
opzhub_drop_root_if_requested "$@"
opzhub_assert_tsuser
opzhub_layout
opzhub_prepare_dirs
opzhub_cd_app

JAVA_BIN="${JAVA_BIN:-java}"
opzhub_require_cmd "${JAVA_BIN}"

JAR="${OPZHUB_JAR:-${OPZHUB_HOME}/opzhub-be-app.jar}"
[ -r "${JAR}" ] || opzhub_die "application jar not readable: ${JAR}"

exec "${JAVA_BIN}" \
  -Dfile.encoding=UTF-8 \
  -Djava.io.tmpdir="${OPZHUB_TMP}" \
  -Duser.home="${HOME}" \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath="${OPZHUB_DATA}/data/heap.hprof" \
  -jar "${JAR}" \
  "$@"
