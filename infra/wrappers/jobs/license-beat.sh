#!/usr/bin/env bash
# Optional heartbeat to the central license server. No-op if hub URL unset.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout

HUB="${LICENSE_HUB_URL:-}"
LOG="${OPZHUB_LOGS}/jobs/license-beat.log"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

if [ -z "${HUB}" ]; then
  printf '%s skip: LICENSE_HUB_URL empty\n' "${STAMP}" >>"${LOG}"
  exit 0
fi

if [ -f "${OPZHUB_CONFIG}/secrets.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "${OPZHUB_CONFIG}/secrets.env"
  set +a
fi

if command -v curl >/dev/null 2>&1; then
  curl -fsS --max-time 20 -X POST "${HUB%/}/api/v1/license/heartbeat" \
    -H "Authorization: Bearer ${LICENSE_HUB_TOKEN:-}" \
    -H "Content-Type: application/json" \
    -d "{\"instance\":\"${OPZHUB_INSTANCE_ID:-unknown}\",\"company\":\"${OPZHUB_SOLUTION_ID:-}\"}" \
    >/dev/null || printf '%s heartbeat failed\n' "${STAMP}" >>"${LOG}"
fi
printf '%s license-beat ok\n' "${STAMP}" >>"${LOG}"
exit 0
