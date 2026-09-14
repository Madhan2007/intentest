#!/usr/bin/env bash
# One-time host layout for ManageMyOpz. Run as root. Never starts the app.
# Safe to re-run (idempotent). Does not delete /home/tsuser/opzhub (release).
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  printf '%s\n' "init-system: must run as root (one-time OS/site files)" >&2
  exit 77
fi

umask 027

ETC=/etc/opzhub
VARLIB=/var/lib/opzhub
VARLOG=/var/log/opzhub
HOME_TS=/home/tsuser
WRAP_SRC="$(cd "$(dirname "$0")" && pwd)"

if ! getent group tsuser >/dev/null; then
  groupadd --gid 2100 tsuser
fi
if ! id tsuser >/dev/null 2>&1; then
  useradd --system --uid 2100 --gid 2100 --create-home --home-dir "${HOME_TS}" \
    --shell /usr/sbin/nologin tsuser
fi
usermod -aG docker tsuser 2>/dev/null || true

install -d -m 0750 -o tsuser -g tsuser \
  "${ETC}" "${ETC}/certs/public" "${ETC}/certs/internal"

install -d -m 0750 -o tsuser -g tsuser \
  "${VARLIB}" "${VARLIB}/data" "${VARLIB}/data/staging" "${VARLIB}/data/web-cache" \
  "${VARLIB}/run" "${VARLIB}/run/locks" "${VARLIB}/run/svc" "${VARLIB}/tmp" \
  "${VARLIB}/backup" "${VARLIB}/backup/db" \
  "${VARLOG}" "${VARLOG}/jobs" "${VARLOG}/svc" \
  "${HOME_TS}"

if [ ! -f "${ETC}/opzhub.env" ]; then
  cat >"${ETC}/opzhub.env" <<'EOF'
# One-time machine env. Not replaced by application upgrades.
OPZHUB_APP_NAME=managemyopz
OPZHUB_SHORT=opzhub
OPZHUB_APP_USER=tsuser
OPZHUB_APP_UID=2100
OPZHUB_APP_GID=2100
HOME=/home/tsuser
OPZHUB_HOME=/home/tsuser/opzhub
OPZHUB_CONFIG=/etc/opzhub
OPZHUB_DATA=/var/lib/opzhub
OPZHUB_LOGS=/var/log/opzhub
OPZHUB_RUN=/var/lib/opzhub/run
OPZHUB_LOCKS=/var/lib/opzhub/run/locks
OPZHUB_TMP=/var/lib/opzhub/tmp
OPZHUB_CERTS=/etc/opzhub/certs
PLATFORM_CONFIG_PATH=/etc/opzhub/platform.override.yaml
# OPZHUB_SOLUTION_ID=
# OPZHUB_RUNTIME=auto
# OPZHUB_BACKUP_LOCAL=1
# OPZHUB_BACKUP_FTP=0
# OPZHUB_BACKUP_KEEP=48
# OPZHUB_BACKUP_FTP_PROTO=ftps
# OPZHUB_BACKUP_FTP_HOST=
# OPZHUB_BACKUP_FTP_DIR=/opzhub
EOF
  chown root:tsuser "${ETC}/opzhub.env"
  chmod 0640 "${ETC}/opzhub.env"
fi

if [ ! -f "${ETC}/secrets.env.example" ]; then
  cat >"${ETC}/secrets.env.example" <<'EOF'
# copy to secrets.env (0640 root:tsuser); never store in the release tree
# LICENSE_HUB_TOKEN=
# BACKUP_FTP_USER=
# BACKUP_FTP_PASSWORD=
EOF
  chown root:tsuser "${ETC}/secrets.env.example"
  chmod 0640 "${ETC}/secrets.env.example"
fi

# Stable stubs — crontab always calls these, not a versioned path only.
for stub in opzhub-run-cron opzhub-run-job opzhubctl; do
  src="${WRAP_SRC}/system/${stub}"
  if [ -f "${src}" ]; then
    install -m 0755 -o root -g root "${src}" "/usr/local/bin/${stub}"
  fi
done

printf '%s\n' "init-system: /etc/opzhub /var/lib/opzhub /var/log/opzhub and tsuser ready (release still goes to ${HOME_TS}/opzhub)"
exit 0
