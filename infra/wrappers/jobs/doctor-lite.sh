#!/usr/bin/env bash
# Non-interactive doctor: site dirs exist; release tree exists; not root.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout

[ "$(id -un)" = "tsuser" ]
[ -d /etc/opzhub ] || exit 1
[ -r /etc/opzhub/opzhub.env ] || exit 1
[ -d /var/lib/opzhub ] || exit 1
[ -d /var/log/opzhub ] || exit 1
[ -d "${OPZHUB_HOME}" ] || exit 1
exit 0
