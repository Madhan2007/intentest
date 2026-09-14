#!/usr/bin/env bash
# Ubuntu 24.04 EC2 bootstrap (doc 09 §5). Run once as root on a fresh
# instance. Installs Docker, creates tsuser, and lays out one-time site
# directories. Does NOT start the stack — that is `opzhubctl start` as tsuser.
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "ec2-bootstrap: must run as root" >&2
  exit 77
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

id tsuser >/dev/null 2>&1 || useradd --system --uid 2100 --create-home --home-dir /home/tsuser --shell /usr/sbin/nologin tsuser
usermod -aG docker tsuser

# One-time host layout (idempotent) — release extraction happens separately
# (copy the packaged tarball to /home/tsuser/opzhub, then run init-home.sh as tsuser).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${SCRIPT_DIR}/wrappers/init-system.sh" ]; then
  bash "${SCRIPT_DIR}/wrappers/init-system.sh"
fi

cat <<'EOF'
ec2-bootstrap: done.

Next steps (see doc 09 §5 and doc 17 §7):
  1. Copy the release tarball to /home/tsuser/opzhub (chown -R tsuser:tsuser).
  2. runuser -u tsuser -- bash /home/tsuser/opzhub/infra/wrappers/init-home.sh
  3. runuser -u tsuser -- bash -c 'cd /home/tsuser/opzhub && docker compose -f infra/docker-compose.yml up -d'
  4. runuser -u tsuser -- /usr/local/bin/opzhubctl migrate
  5. runuser -u tsuser -- /usr/local/bin/opzhubctl health --wait 120

Security group: allow 22 (restricted CIDR), 80, 443 only. Never expose
5432, 6379/6380, 8114, 8117, 8109, 8124 to the internet.
EOF
