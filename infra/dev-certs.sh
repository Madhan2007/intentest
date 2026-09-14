#!/bin/sh
# Self-signed DEV-ONLY certificate for the public Nginx listener. Real CA
# issuance / internal mTLS is doc 12 — not implemented in this pass. Idempotent.
# Uses POSIX sh so the certs one-shot only needs openssl from apk (no bash).
# SPDX-License-Identifier: Apache-2.0
set -eu

CERT_DIR="${OPZHUB_CERTS:-/etc/opzhub/certs}/public"
mkdir -p "${CERT_DIR}"

if [ -f "${CERT_DIR}/fullchain.pem" ] && [ -f "${CERT_DIR}/privkey.pem" ]; then
  echo "dev-certs: public certificate already present — skipping"
  exit 0
fi

openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout "${CERT_DIR}/privkey.pem" \
  -out "${CERT_DIR}/fullchain.pem" \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,DNS:opzgw,IP:127.0.0.1"

chmod 0640 "${CERT_DIR}/privkey.pem"
chmod 0644 "${CERT_DIR}/fullchain.pem"
echo "dev-certs: generated self-signed public certificate (DEV ONLY)"
