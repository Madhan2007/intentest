#!/usr/bin/env bash
# Remove lock/pid files whose process is dead so a later cron is not skipped forever.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout

shopt -s nullglob
for pidf in "${OPZHUB_RUN}"/*.pid; do
  pid="$(cat "${pidf}" 2>/dev/null || true)"
  job="$(basename "${pidf}" .pid)"
  if [ -z "${pid}" ] || ! kill -0 "${pid}" 2>/dev/null; then
    rm -f "${pidf}" "${OPZHUB_LOCKS}/${job}.lock"
  fi
done
exit 0
