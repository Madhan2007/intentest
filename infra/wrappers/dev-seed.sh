#!/usr/bin/env bash
# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-12
# Description:
#   Idempotent dev-environment seed script.
#   Runs INSIDE the opzhub-be-app container via docker compose exec.
#   Uses the container's own DB environment variables and reads the
#   bootstrap admin password directly from the local filesystem.
#
#   Ensures the Technosprint company row, server/license reference data,
#   and the default administrator account are present and consistent.
#   Does NOT change or reset any password.
#
# How to run (from repo root):
#
#   Git Bash / MINGW64:
#     MSYS_NO_PATHCONV=1 docker compose \
#         -f infra/docker-compose.yml \
#         -f infra/docker-compose.dev.yml \
#         exec opzhub-be-app bash infra/wrappers/dev-seed.sh
#
#   PowerShell:
#     docker compose `
#         -f infra/docker-compose.yml `
#         -f infra/docker-compose.dev.yml `
#         exec opzhub-be-app bash infra/wrappers/dev-seed.sh
#
# Prerequisites: stack must be up.
#   docker compose -f infra/docker-compose.yml -f infra/docker-compose.dev.yml up -d
# ---------------------------------------------------------------------------

set -euo pipefail

# ---------------------------------------------------------------------------
# DB connection — sourced from the container's own environment variables
# ---------------------------------------------------------------------------
PG_HOST="${DB_POSTGRES_HOST:-postgres}"
PG_PORT="${DB_POSTGRES_PORT:-5432}"
PG_USER="${DB_POSTGRES_USER:-erp_app}"
export PGPASSWORD="${DB_POSTGRES_PASSWORD:-devpassword}"

# ---------------------------------------------------------------------------
# Seed values
# ---------------------------------------------------------------------------
ADMIN_USERNAME="admin"
ADMIN_EMAIL="admin@technosprint.net"
ADMIN_DISPLAY_NAME="Administrator"

COMPANY_NAME="Technosprint"
COMPANY_SLUG="technosprint"
COMPANY_REFERENCES='["technosprint","technosprint.net"]'
COMPANY_ROUTING="CENTRAL_DB"
COMPANY_STATUS="active"

LICENSE_CODE="DEV"
LICENSE_EXPIRY=4102444800    # 2100-01-01 epoch

SERVER_NAME="local"
SERVER_DB_IP="postgres"
SERVER_DB_PORT=5432
SERVER_DB_USER="erp_app"
SERVER_DB_PASS="devpassword"

BOOTSTRAP_PASSWORD_FILE="/etc/opzhub/bootstrap-admin-password"

# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------
banner() { printf '\n==> %s\n' "$*"; }

pg() {
    # pg <database> <sql>
    psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" \
         -d "$1" -v ON_ERROR_STOP=1 -c "$2"
}

# ---------------------------------------------------------------------------
# Pre-flight
# ---------------------------------------------------------------------------
banner "Checking PostgreSQL connection (${PG_HOST}:${PG_PORT})"
pg erp "SELECT 1" >/dev/null
echo "OK: connected as ${PG_USER}@${PG_HOST}"

# ---------------------------------------------------------------------------
# 1. opzmain — company_license  (FK prerequisite)
# ---------------------------------------------------------------------------
banner "opzmain: company_license"
pg opzmain "
INSERT INTO company_license
    (license_code, license_expiry_epoch, license_file, license_details)
VALUES
    ('${LICENSE_CODE}', ${LICENSE_EXPIRY}, NULL, NULL)
ON CONFLICT (license_code) DO NOTHING;
"

# ---------------------------------------------------------------------------
# 2. opzmain — server_details  (FK prerequisite)
# ---------------------------------------------------------------------------
banner "opzmain: server_details"
pg opzmain "
INSERT INTO server_details
    (server_name, db_ip, db_port, db_username, db_password)
VALUES
    ('${SERVER_NAME}', '${SERVER_DB_IP}', ${SERVER_DB_PORT},
     '${SERVER_DB_USER}', '${SERVER_DB_PASS}')
ON CONFLICT (server_name) DO NOTHING;
"

# ---------------------------------------------------------------------------
# 3. opzmain — company_information schema guard (v1→v2)
#    Adds missing v2 columns without failing if they already exist.
# ---------------------------------------------------------------------------
banner "opzmain: company_information schema (v1→v2 migration guard)"
pg opzmain "
ALTER TABLE company_information
    ADD COLUMN IF NOT EXISTS company_slug   TEXT,
    ADD COLUMN IF NOT EXISTS routing_mode   TEXT NOT NULL DEFAULT 'CENTRAL_DB',
    ADD COLUMN IF NOT EXISTS endpoint_name  TEXT,
    ADD COLUMN IF NOT EXISTS logo_uri       TEXT,
    ADD COLUMN IF NOT EXISTS status         TEXT NOT NULL DEFAULT 'active';

CREATE UNIQUE INDEX IF NOT EXISTS idx_company_information_company_slug
    ON company_information (company_slug);
CREATE INDEX IF NOT EXISTS idx_company_information_endpoint_name
    ON company_information (endpoint_name);
"

# ---------------------------------------------------------------------------
# 4. opzmain — Technosprint company row
# ---------------------------------------------------------------------------
banner "opzmain: company_information row"
pg opzmain "
INSERT INTO company_information
    (company_name, company_slug, company_references,
     license_code, server_name, routing_mode, status)
VALUES
    ('${COMPANY_NAME}', '${COMPANY_SLUG}', '${COMPANY_REFERENCES}',
     '${LICENSE_CODE}', '${SERVER_NAME}', '${COMPANY_ROUTING}', '${COMPANY_STATUS}')
ON CONFLICT (company_slug) DO UPDATE
    SET company_name       = EXCLUDED.company_name,
        company_references = EXCLUDED.company_references,
        routing_mode       = EXCLUDED.routing_mode,
        status             = EXCLUDED.status;

UPDATE company_information
    SET company_slug = '${COMPANY_SLUG}'
WHERE company_name = '${COMPANY_NAME}' AND company_slug IS NULL;
"

# ---------------------------------------------------------------------------
# 5. Read back the company UUID
# ---------------------------------------------------------------------------
COMPANY_ID=$(
    pg opzmain \
        "SELECT id FROM company_information WHERE company_slug = '${COMPANY_SLUG}';" \
        2>/dev/null \
    | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
)

if [ -z "${COMPANY_ID}" ]; then
    echo "ERROR: Could not read company id — check the opzmain insert above."
    exit 1
fi
echo "INFO: company id = ${COMPANY_ID}"

# ---------------------------------------------------------------------------
# 6. opzuser — admin user
#    INSERT only when absent; UPDATE restores metadata without touching
#    password_hash so the server-generated password is preserved.
# ---------------------------------------------------------------------------
banner "opzuser: admin user (company_id=${COMPANY_ID})"
pg opzuser "
INSERT INTO id_user
    (username, email, display_name, password_hash, roles, enabled, company_id)
SELECT
    '${ADMIN_USERNAME}',
    '${ADMIN_EMAIL}',
    '${ADMIN_DISPLAY_NAME}',
    COALESCE(
        (SELECT password_hash FROM id_user WHERE username = '${ADMIN_USERNAME}'),
        'PLACEHOLDER-restart-stack-to-bootstrap-password'
    ),
    ARRAY['admin']::text[],
    true,
    '${COMPANY_ID}'
WHERE NOT EXISTS (SELECT 1 FROM id_user WHERE username = '${ADMIN_USERNAME}');

UPDATE id_user
    SET email        = '${ADMIN_EMAIL}',
        display_name = '${ADMIN_DISPLAY_NAME}',
        roles        = ARRAY['admin']::text[],
        enabled      = true,
        company_id   = '${COMPANY_ID}'
WHERE username = '${ADMIN_USERNAME}';
"

# ---------------------------------------------------------------------------
# 7. opzhub — enable applications for the Technosprint company
#    Covers every app that has a folder under apps/ and a matching row in
#    application_catalog.  State is set to 'licensed' (company has permission
#    to use the app; admin installs it separately to place it on the dashboard).
#    Uses ON CONFLICT so it is fully idempotent.
#    Apps not yet in application_catalog are silently skipped.
# ---------------------------------------------------------------------------
banner "opzhub: company application licenses"
pg opzhub "
INSERT INTO company_application
    (id, company_id, application_id, license_state, favourite)
SELECT
    gen_random_uuid(),
    '${COMPANY_ID}',
    ac.id,
    'licensed',
    true
FROM application_catalog ac
WHERE ac.app_key = ANY(ARRAY[
    'manage-my-data',
    'manage-my-desk',
    'manage-my-hr',
    'manage-my-market',
    'manage-my-marketing',
    'manage-my-finance',
    'manage-my-sales',
    'manage-my-inventory',
    'manage-my-shop',
    'manage-my-project',
    'manage-my-vault'
])
ON CONFLICT (company_id, application_id) DO UPDATE
    SET license_state = 'licensed',
        favourite     = true;
"

# ---------------------------------------------------------------------------
# 8. Read admin password from this container's bootstrap file
# ---------------------------------------------------------------------------
banner "Admin password"
ADMIN_PASSWORD=""
if [ -f "${BOOTSTRAP_PASSWORD_FILE}" ]; then
    ADMIN_PASSWORD=$(cat "${BOOTSTRAP_PASSWORD_FILE}" | tr -d '[:space:]')
fi

# ---------------------------------------------------------------------------
# 9. Verify
# ---------------------------------------------------------------------------
banner "Verification"

echo ""
echo "--- opzmain.company_information ---"
pg opzmain "
SELECT id, company_name, company_slug, routing_mode, status, company_references
  FROM company_information;
"

echo ""
echo "--- opzuser.id_user ---"
pg opzuser "
SELECT id, username, email, roles, enabled, company_id
  FROM id_user;
"

echo ""
echo "--- opzhub.company_application (licensed) ---"
pg opzhub "
SELECT ac.app_key, ac.product_code, ca.license_state, ca.favourite
  FROM company_application ca
  JOIN application_catalog  ac ON ac.id = ca.application_id
 WHERE ca.company_id = '${COMPANY_ID}'
 ORDER BY ac.sort_order;
"

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
if [ -n "${ADMIN_PASSWORD}" ]; then
    PASSWORD_LINE="    Password  : ${ADMIN_PASSWORD}"
    PASSWORD_NOTE=""
else
    PASSWORD_LINE="    Password  : (not yet generated — restart the stack)"
    PASSWORD_NOTE="
  NOTE: The bootstrap password file is missing. This happens when the app
  started before the DB was ready and skipped bootstrap. Restart the stack:
    docker compose -f infra/docker-compose.yml \\
                   -f infra/docker-compose.dev.yml restart opzhub-be-app
"
fi

cat <<EOF

===========================================================
  Dev seed complete.

  Login credentials (DEV ONLY — never use in production):
    URL       : http://localhost:8114/api/v1/opzhub/identity/login
    Username  : ${ADMIN_EMAIL}
${PASSWORD_LINE}
    Alt login : ${COMPANY_SLUG}/${ADMIN_USERNAME}
${PASSWORD_NOTE}
  ── Run this seed again at any time ─────────────────────

  Git Bash / MINGW64:
    MSYS_NO_PATHCONV=1 docker compose \\
        -f infra/docker-compose.yml \\
        -f infra/docker-compose.dev.yml \\
        exec opzhub-be-app bash infra/wrappers/dev-seed.sh

  PowerShell:
    docker compose \`
        -f infra/docker-compose.yml \`
        -f infra/docker-compose.dev.yml \`
        exec opzhub-be-app bash infra/wrappers/dev-seed.sh

  ── Retrieve password only ───────────────────────────────

  Git Bash / MINGW64:
    MSYS_NO_PATHCONV=1 docker compose \\
        -f infra/docker-compose.yml \\
        -f infra/docker-compose.dev.yml \\
        exec opzhub-be-app cat /etc/opzhub/bootstrap-admin-password

  PowerShell:
    docker compose \`
        -f infra/docker-compose.yml \`
        -f infra/docker-compose.dev.yml \`
        exec opzhub-be-app cat /etc/opzhub/bootstrap-admin-password

===========================================================
EOF
