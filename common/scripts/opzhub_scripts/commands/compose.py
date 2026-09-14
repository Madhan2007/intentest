# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""`opzhubctl compose` — up/down/logs wrapper selecting profiles from the
manifest and auth methods (doc 06 §3, doc 09 §2.1, doc 17 §4)."""
from __future__ import annotations

import os
import subprocess
import sys

from opzhub_scripts import config, logging as opzlog


def _compose_files(dev: bool) -> list[str]:
    files = ["infra/docker-compose.yml"]
    if dev:
        files.append("infra/docker-compose.dev.yml")
    return [f for f in files if os.path.isfile(f)]


def _profiles(settings) -> list[str]:
    profiles: list[str] = []
    methods = settings.get("security.auth.methods", []) or []
    if "face" in methods or "fingerprint" in methods or os.path.isdir("modules/ocr") \
            or os.path.isdir("modules/image-processing") or os.path.isdir("modules/mail"):
        profiles.append("ai")
    if os.path.isdir("modules/ocr"):
        profiles.append("ocr")
    if os.path.isdir("modules/image-processing"):
        profiles.append("image")
    # Kafka is optional/later and is never selected here (doc 11).
    return profiles


def run(args) -> int:
    settings = config.load()
    action = args.action
    dev = getattr(args, "dev", False)
    files = _compose_files(dev)
    if not files:
        print("compose: no infra/docker-compose.yml found")
        return 2

    cmd = ["docker", "compose", "--project-name", os.environ.get("OPZHUB_COMPOSE_PROJECT", "opzhub")]
    for f in files:
        cmd += ["-f", f]
    for p in _profiles(settings):
        cmd += ["--profile", p]

    if action == "up":
        cmd += ["up", "-d"]
    elif action == "down":
        cmd += ["down"]
    elif action == "logs":
        cmd += ["logs", "-f"]
        if args.service:
            cmd.append(args.service)
    else:
        print(f"compose: unknown action {action}")
        return 5

    opzlog.log("compose", cmd=cmd)
    result = subprocess.run(cmd)
    return result.returncode
