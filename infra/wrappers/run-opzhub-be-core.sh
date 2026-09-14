#!/usr/bin/env bash
# Runs the Python API and worker in the single opzhub-be-core container.
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

PYTHON_BIN="${PYTHON_BIN:-python3}"
opzhub_require_cmd "${PYTHON_BIN}"

export OMP_NUM_THREADS="${OMP_NUM_THREADS:-2}"
export OPENCV_FOR_THREADS_NUM="${OPENCV_FOR_THREADS_NUM:-2}"
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"
export TMPDIR="${OPZHUB_TMP}"

"${PYTHON_BIN}" -m opzhub_kernel.workers &
WORKER_PID=$!

"${PYTHON_BIN}" -m uvicorn \
  "${OPZHUB_AI_MODULE:-opzhub_kernel.app_factory:create_app}" \
  --factory \
  --host "${OPZHUB_AI_BIND_HOST:-0.0.0.0}" \
  --port "${OPZHUB_AI_BIND_PORT:-8117}" \
  --proxy-headers \
  --no-server-header &
API_PID=$!

stop_children() {
  kill -TERM "${API_PID}" "${WORKER_PID}" 2>/dev/null || true
  wait "${API_PID}" "${WORKER_PID}" 2>/dev/null || true
}

trap stop_children TERM INT

set +e
wait -n "${API_PID}" "${WORKER_PID}"
STATUS=$?
set -e
stop_children
exit "${STATUS}"