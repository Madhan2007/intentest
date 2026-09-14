#!/usr/bin/env bash
# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-13
# Description:
#   Builds a product-scoped package: repo root minus the apps/<id>/ folders
#   that are not listed for the requested product in
#   platform/catalog/products.yaml (doc 35 §3).
#
#   Every app under apps/ must be listed under exactly one product's
#   "apps:" list in products.yaml for this script to include it. An app not
#   listed anywhere is excluded from every product build.
#
# Usage (from repo root):
#   bash infra/wrappers/package-product.sh [--product KEY] [--out DIR]
#
#   --product KEY   Product key from platform/catalog/products.yaml.
#                    Defaults to "managemyopz" when omitted.
#   --out DIR        Output directory. Defaults to dist/<product-key>.
#
# --product is chosen HERE ONLY — this is the one and only place in the
# whole system that names a product on the command line. The resolved key
# is stamped into <out>/platform/config/product.yaml so every runtime
# component (opzhubctl, the seed at boot, the Java app) picks it up
# automatically from the deployed package or from the database — never
# from a repeated --product argument (doc 35 §3.5).
#
# This is a separate, standalone script — NOT part of opzhubctl. The
# planned `opzhubctl package --solution <path>` (doc 01 §6) is a different,
# pre-existing packaging axis: it filters modules/<id>/ for one customer's
# solution manifest. This script filters apps/<id>/ for one product. Run
# this one first; a customer solution pack can be built from its output.
# ---------------------------------------------------------------------------

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PRODUCTS_FILE="${REPO_ROOT}/platform/catalog/products.yaml"

PRODUCT_KEY="managemyopz"
OUT_DIR=""

while [ $# -gt 0 ]; do
    case "$1" in
        --product)
            PRODUCT_KEY="$2"
            shift 2
            ;;
        --out)
            OUT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if [ -z "${OUT_DIR}" ]; then
    OUT_DIR="${REPO_ROOT}/dist/${PRODUCT_KEY}"
fi

if [ ! -f "${PRODUCTS_FILE}" ]; then
    echo "ERROR: ${PRODUCTS_FILE} not found." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Extract the requested product's "apps:" list from products.yaml.
# products.yaml is a flat, hand-authored list-of-maps structure (doc 35 §3);
# this avoids a hard dependency on yq/python being present on the build host.
# ---------------------------------------------------------------------------
extract_apps() {
    awk -v key="product_key: ${PRODUCT_KEY}" '
        /^  - product_key:/ {
            in_block = ($0 ~ key)
            next
        }
        in_block && /^    apps:/ { in_apps = 1; next }
        in_block && in_apps && /^      - / {
            line = $0
            sub(/^      - /, "", line)
            print line
            next
        }
        in_block && in_apps && !/^      - / { in_apps = 0 }
    ' "${PRODUCTS_FILE}"
}

mapfile -t PRODUCT_APPS < <(extract_apps)

if [ ${#PRODUCT_APPS[@]} -eq 0 ]; then
    echo "ERROR: product '${PRODUCT_KEY}' not found in products.yaml, or its apps: list is empty." >&2
    echo "       Placeholder products (managemyid, smartaicampus) cannot be packaged" >&2
    echo "       until at least one apps/<id>/ folder is listed for them." >&2
    exit 1
fi

echo "==> Packaging product '${PRODUCT_KEY}'"
echo "    Apps requested: ${PRODUCT_APPS[*]}"

# ---------------------------------------------------------------------------
# Resolve which requested apps actually have a folder on disk.
# ---------------------------------------------------------------------------
INCLUDED_APPS=()
MISSING_APPS=()
for app in "${PRODUCT_APPS[@]}"; do
    if [ -d "${REPO_ROOT}/apps/${app}" ]; then
        INCLUDED_APPS+=("${app}")
    else
        MISSING_APPS+=("${app}")
    fi
done

if [ ${#INCLUDED_APPS[@]} -eq 0 ]; then
    echo "ERROR: none of product '${PRODUCT_KEY}'s listed apps have a folder under apps/." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Compute excluded apps (folders on disk that belong to a different product,
# or to no product at all) — printed for visibility, never copied.
# ---------------------------------------------------------------------------
EXCLUDED_APPS=()
for dir in "${REPO_ROOT}"/apps/*/; do
    name="$(basename "${dir}")"
    found=0
    for app in "${INCLUDED_APPS[@]}"; do
        [ "${app}" = "${name}" ] && found=1 && break
    done
    [ "${found}" -eq 0 ] && EXCLUDED_APPS+=("${name}")
done

# ---------------------------------------------------------------------------
# Build the output pack.
# ---------------------------------------------------------------------------
rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

echo "==> Copying shared trees (common, modules, platform, infra, tools, gateway)"
for shared in common modules platform infra tools gateway; do
    if [ -d "${REPO_ROOT}/${shared}" ]; then
        cp -a "${REPO_ROOT}/${shared}" "${OUT_DIR}/${shared}"
    fi
done

echo "==> Copying included apps/ folders"
mkdir -p "${OUT_DIR}/apps"
for app in "${INCLUDED_APPS[@]}"; do
    cp -a "${REPO_ROOT}/apps/${app}" "${OUT_DIR}/apps/${app}"
done

# ---------------------------------------------------------------------------
# Stamp the resolved product into the package. This is the ONLY artifact
# any runtime component reads to know "which product is this deployment" —
# no script or command downstream of this one ever takes --product again.
# ---------------------------------------------------------------------------
mkdir -p "${OUT_DIR}/platform/config"
cat > "${OUT_DIR}/platform/config/product.yaml" <<EOF
# Written once by infra/wrappers/package-product.sh --product ${PRODUCT_KEY}
# on $(date -u +"%Y-%m-%dT%H:%M:%SZ"). Do not hand-edit; re-run the packaging
# script to change the product. Read at boot by the platform product seed
# (doc 35 §3.5), which stores this value into a single-row DB table shared
# by OPZMAIN and OPZHUB so no runtime code ever needs --product again.
product_key: ${PRODUCT_KEY}
EOF

# ---------------------------------------------------------------------------
# Manifest for the pack.
# ---------------------------------------------------------------------------
cat > "${OUT_DIR}/PRODUCT_BUILD.md" <<EOF
# Product build: ${PRODUCT_KEY}

Generated by \`infra/wrappers/package-product.sh\` on $(date -u +"%Y-%m-%dT%H:%M:%SZ").

## Apps included
$(for app in "${INCLUDED_APPS[@]}"; do echo "- ${app}"; done)

## Apps requested but missing a folder (not included)
$(if [ ${#MISSING_APPS[@]} -eq 0 ]; then echo "(none)"; else for app in "${MISSING_APPS[@]}"; do echo "- ${app}"; done; fi)

## Apps excluded (belong to a different product or no product)
$(if [ ${#EXCLUDED_APPS[@]} -eq 0 ]; then echo "(none)"; else for app in "${EXCLUDED_APPS[@]}"; do echo "- ${app}"; done; fi)

## Product stamp
Written to \`platform/config/product.yaml\`. The running backend reads this
once at boot and stores it in a single-row DB table shared by OPZMAIN and
OPZHUB — no further step in this package ever needs \`--product\` again.
EOF

echo ""
echo "==> Product build complete: ${OUT_DIR}"
echo "    Included apps  : ${INCLUDED_APPS[*]}"
if [ ${#MISSING_APPS[@]} -gt 0 ]; then
    echo "    Missing folders : ${MISSING_APPS[*]}"
fi
if [ ${#EXCLUDED_APPS[@]} -gt 0 ]; then
    echo "    Excluded apps   : ${EXCLUDED_APPS[*]}"
fi
echo ""
echo "Next: docker compose -f ${OUT_DIR}/infra/docker-compose.yml build"
