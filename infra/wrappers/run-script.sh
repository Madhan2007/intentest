#!/usr/bin/env bash
# Run an arbitrary script under lock+timeout (same as cron). Use from CLI.
#   run-script.sh myscript -- /home/tsuser/opzhub/modules/ledger/scripts/commands/seed.sh
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
exec "${ROOT}/run-job.sh" "$@"
