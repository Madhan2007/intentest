#!/usr/bin/env bash
# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-13
# Description:
#   Dev convenience wrapper: packages the requested product (doc 35 §4) and
#   runs `docker compose` against that package's output tree, in one command.
#
#   --product only matters at BUILD time. Pass it when you build to select
#   or switch which product's apps get baked into the image; leave it off
#   for every other command (up, down, logs, ps, exec, ...) and this script
#   just reuses whatever product was packaged by the most recent build —
#   it never silently falls back to a different product underneath you.
#
# Usage (from repo root):
#   bash infra/wrappers/dev-compose.sh --product KEY <build-related compose args...>
#   bash infra/wrappers/dev-compose.sh <any other compose args...>
#
#   --product KEY   Product key from platform/catalog/products.yaml.
#                    Only meaningful together with a command that builds
#                    (`build`, or `up ... --build`). Other values today:
#                    managemyid, smartaicampus (placeholders — no apps/
#                    folders yet, packaging refuses until they do). If this
#                    is the very first build in the repo and --product is
#                    omitted, it defaults to "managemyopz".
#   Everything else is forwarded to `docker compose` verbatim, after the
#   `-f <out>/infra/docker-compose.yml -f <out>/infra/docker-compose.dev.yml`
#   flags this script fills in for you.
#
# Examples:
#   bash infra/wrappers/dev-compose.sh --product managemyopz --progress plain build
#   bash infra/wrappers/dev-compose.sh up -d                    # no --product — reuses the last build
#   bash infra/wrappers/dev-compose.sh logs -f opzhub-be-app
#   bash infra/wrappers/dev-compose.sh down -v
#   bash infra/wrappers/dev-compose.sh --product managemyid --progress plain build   # switch product
#   bash infra/wrappers/dev-compose.sh up -d                    # now starts managemyid
#
# This does NOT introduce a second place that decides "what is the product"
# (doc 35 §4.1) — it only ever calls infra/wrappers/package-product.sh
# internally, which remains the sole packaging authority and the sole
# writer of <out>/platform/config/product.yaml. Every downstream component
# (the seed at boot, the running app) still learns the product by reading
# that stamped file, never by a repeated argument.
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUT_DIR="${REPO_ROOT}/dist/current"

PRODUCT_KEY=""
COMPOSE_ARGS=()

while [ $# -gt 0 ]; do
    case "$1" in
        --product)
            PRODUCT_KEY="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '2,42p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            COMPOSE_ARGS+=("$1")
            shift
            ;;
    esac
done

if [ -n "${PRODUCT_KEY}" ]; then
    echo "==> Packaging product '${PRODUCT_KEY}' into dist/current"
    bash "${SCRIPT_DIR}/package-product.sh" --product "${PRODUCT_KEY}" --out "${OUT_DIR}"
elif [ ! -f "${OUT_DIR}/infra/docker-compose.yml" ]; then
    echo "==> No packaged product yet — defaulting first build to 'managemyopz'"
    bash "${SCRIPT_DIR}/package-product.sh" --product managemyopz --out "${OUT_DIR}"
else
    STAMPED_KEY="$(grep '^product_key:' "${OUT_DIR}/platform/config/product.yaml" 2>/dev/null | awk '{print $2}')"
    echo "==> Reusing already-packaged product '${STAMPED_KEY:-unknown}' (dist/current) — pass --product to build a different one"
fi

exec docker compose \
    -f "${OUT_DIR}/infra/docker-compose.yml" \
    -f "${OUT_DIR}/infra/docker-compose.dev.yml" \
    "${COMPOSE_ARGS[@]}"
