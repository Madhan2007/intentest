#!/usr/bin/env bash
# Trim logs under /var/log/opzhub only (survives application upgrades).
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout

KEEP_MB="${OPZHUB_LOG_KEEP_MB:-50}"
find "${OPZHUB_LOGS}" -type f -name '*.log' -size +"${KEEP_MB}"M -print0 2>/dev/null \
  | xargs -0 -r truncate -s 0
find "${OPZHUB_LOGS}/jobs" -type f -name '*.log' -mtime +14 -delete 2>/dev/null || true
exit 0
