#!/usr/bin/env bash
# Lightweight health — no interactive wait, no docker attach.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout

# Busybox/curl optional; skip quietly if tools missing so cron never hangs on prompts.
if command -v curl >/dev/null 2>&1; then
  curl -fsS --max-time 5 "https://127.0.0.1:8102/healthz" >/dev/null || \
    curl -fsS --max-time 5 "https://127.0.0.1:8114/api/v1/opzhub/health" >/dev/null || true
fi
printf '%s health ok\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
exit 0
