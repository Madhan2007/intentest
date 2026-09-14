#!/usr/bin/env python3
# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Prints extra Nginx `location` blocks for each enabled module's
`provides.api_prefixes` (doc 02 §10, doc 09 §4.1). Kernel prefixes
(/api/v1/opzhub/identity, /health, /meta/*) are already in
gateway/nginx.conf.template and are not repeated here.

Usage (from repo root):
    python tools/gen_nginx_locations.py

Output is meant to be reviewed and pasted into gateway/nginx.conf — this is
a generator, not a templating engine that overwrites the gateway config.
"""
from __future__ import annotations

import glob
import os
import sys

import yaml

KERNEL_PREFIXES = {"/api/v1/opzhub/identity", "/api/v1/opzhub/health", "/api/v1/opzhub/meta"}


def load_platform_yaml() -> dict:
    for prefix in ("", "../", "../../"):
        path = os.path.join(prefix, "platform", "config", "platform.yaml")
        if os.path.isfile(path):
            with open(path, "r", encoding="utf-8") as f:
                return yaml.safe_load(f) or {}
    return {}


def present_modules() -> dict[str, dict]:
    present: dict[str, dict] = {}
    for prefix in ("", "../", "../../"):
        found = glob.glob(os.path.join(prefix, "modules", "*", "module.yaml"))
        if not found:
            continue
        for path in found:
            with open(path, "r", encoding="utf-8") as f:
                doc = yaml.safe_load(f) or {}
            if doc.get("id"):
                present[doc["id"]] = doc
        break
    return present


def main() -> int:
    platform = load_platform_yaml()
    enabled = set((platform.get("modules") or {}).get("enabled") or [])
    present = present_modules()

    blocks: list[str] = []
    for module_id in sorted(enabled & present.keys()):
        meta = present[module_id]
        for prefix in (meta.get("provides") or {}).get("api_prefixes", []):
            if prefix in KERNEL_PREFIXES or not prefix.startswith("/api/v1/opzhub/"):
                continue
            blocks.append(
                f"  location {prefix}/ {{\n"
                f"    proxy_pass http://opzhub_engine;\n"
                f"    proxy_set_header Host $host;\n"
                f"    proxy_set_header X-Forwarded-Proto https;\n"
                f"  }}\n"
            )

    if not blocks:
        print("# No non-kernel module API prefixes to add (kernel-dev pack: identity + admin only).")
        return 0

    print("# Generated module API locations — review before pasting into gateway/nginx.conf")
    print("\n".join(blocks))
    return 0


if __name__ == "__main__":
    sys.exit(main())
