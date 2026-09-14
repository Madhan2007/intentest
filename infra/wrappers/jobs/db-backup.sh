#!/usr/bin/env bash
# Hourly Postgres dump: local /var/lib/opzhub/backup/db and/or FTP(S)/SFTP.
# Invoked via run-cron.sh db-backup → run-job.sh (lock + timeout).
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=../lib/opzhub-common.sh
. "${ROOT}/lib/opzhub-common.sh"
opzhub_assert_tsuser
opzhub_layout
opzhub_prepare_dirs

if [ -f "${OPZHUB_CONFIG}/secrets.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "${OPZHUB_CONFIG}/secrets.env"
  set +a
fi

LOCAL_ON="${OPZHUB_BACKUP_LOCAL:-1}"
FTP_ON="${OPZHUB_BACKUP_FTP:-0}"
KEEP_LOCAL="${OPZHUB_BACKUP_KEEP:-48}"
PROTO="${OPZHUB_BACKUP_FTP_PROTO:-ftps}"
FTP_HOST="${OPZHUB_BACKUP_FTP_HOST:-}"
FTP_PORT="${OPZHUB_BACKUP_FTP_PORT:-21}"
FTP_USER="${BACKUP_FTP_USER:-${OPZHUB_BACKUP_FTP_USER:-}}"
FTP_PASS="${BACKUP_FTP_PASSWORD:-${OPZHUB_BACKUP_FTP_PASSWORD:-}}"
FTP_DIR="${OPZHUB_BACKUP_FTP_DIR:-/opzhub}"
SOLUTION="${OPZHUB_SOLUTION_ID:-kernel}"
STAMP="$(date -u +%Y%m%dT%H%MZ)"
BASE="opzhub-${SOLUTION}-${STAMP}.dump"
DIR="${OPZHUB_DATA}/backup/db"
mkdir -p "${DIR}"
OUT="${DIR}/${BASE}"
LOG="${OPZHUB_LOGS}/jobs/db-backup.log"

if [ "${LOCAL_ON}" != "1" ] && [ "${FTP_ON}" != "1" ]; then
  printf '%s skip: no backup target (set OPZHUB_BACKUP_LOCAL=1 and/or OPZHUB_BACKUP_FTP=1)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${LOG}"
  exit 0
fi

dump_db() {
  local dest="$1"
  if command -v docker >/dev/null 2>&1; then
    local cid
    cid="$(docker ps --filter "name=postgres" --format '{{.ID}}' | head -n 1 || true)"
    if [ -n "${cid}" ]; then
      docker exec "${cid}" pg_dump -Fc -U "${POSTGRES_USER:-opzhub_app}" -d "${POSTGRES_DB:-opzhub}" >"${dest}"
      return 0
    fi
  fi
  if command -v pg_dump >/dev/null 2>&1; then
    PGPASSWORD="${POSTGRES_PASSWORD:-}" pg_dump -Fc \
      -h "${POSTGRES_HOST:-127.0.0.1}" \
      -p "${POSTGRES_PORT:-5432}" \
      -U "${POSTGRES_USER:-opzhub_app}" \
      -d "${POSTGRES_DB:-opzhub}" \
      >"${dest}"
    return 0
  fi
  return 1
}

if ! dump_db "${OUT}"; then
  printf '%s fail: pg_dump not available (docker postgres or host pg_dump)\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${LOG}"
  rm -f "${OUT}"
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "${OUT}" >"${OUT}.sha256"
fi

upload_ftp() {
  local file="$1" name="$2" url
  [ -n "${FTP_HOST}" ] || { printf '%s ftp host missing\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${LOG}"; return 1; }
  command -v curl >/dev/null 2>&1 || { printf '%s curl missing for ftp\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${LOG}"; return 1; }
  case "${PROTO}" in
    sftp)
      url="sftp://${FTP_HOST}:${FTP_PORT}${FTP_DIR%/}/${name}"
      curl -fsS --connect-timeout 20 --max-time 1800 \
        --user "${FTP_USER}:${FTP_PASS}" -T "${file}" "${url}"
      ;;
    ftps)
      url="ftp://${FTP_HOST}:${FTP_PORT}${FTP_DIR%/}/${name}"
      curl -fsS --connect-timeout 20 --max-time 1800 --ssl-reqd --ftp-create-dirs \
        --user "${FTP_USER}:${FTP_PASS}" -T "${file}" "${url}"
      ;;
    ftp)
      if [ "${OPZHUB_PROFILE:-prod}" = "prod" ]; then
        printf '%s refuse plain ftp in prod\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${LOG}"
        return 1
      fi
      url="ftp://${FTP_HOST}:${FTP_PORT}${FTP_DIR%/}/${name}"
      curl -fsS --connect-timeout 20 --max-time 1800 --ftp-create-dirs \
        --user "${FTP_USER}:${FTP_PASS}" -T "${file}" "${url}"
      ;;
    *)
      printf '%s unknown ftp proto %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${PROTO}" >>"${LOG}"
      return 1
      ;;
  esac
}

if [ "${FTP_ON}" = "1" ]; then
  upload_ftp "${OUT}" "${BASE}"
  if [ -f "${OUT}.sha256" ]; then
    upload_ftp "${OUT}.sha256" "${BASE}.sha256" || true
  fi
fi

if [ "${LOCAL_ON}" != "1" ]; then
  rm -f "${OUT}" "${OUT}.sha256"
else
  # Keep newest KEEP_LOCAL dumps; ignore prune errors.
  if [ "${KEEP_LOCAL}" -gt 0 ] 2>/dev/null; then
    # shellcheck disable=SC2012
    ls -1t "${DIR}"/opzhub-*.dump 2>/dev/null | tail -n "+$((KEEP_LOCAL + 1))" | while read -r old; do
      rm -f "${old}" "${old}.sha256"
    done || true
  fi
fi

printf '%s backup ok file=%s local=%s ftp=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${BASE}" "${LOCAL_ON}" "${FTP_ON}" >>"${LOG}"
exit 0
