# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""`opzhubctl doctor` — validates the merged platform config (doc 08 §7).
`--as-prod` applies production rules regardless of `solution.profile`."""
from __future__ import annotations

from opzhub_scripts import config, logging as opzlog


def _rules(settings, as_prod: bool) -> list[str]:
    errors: list[str] = []
    profile = settings.get("solution.profile", "dev")
    enforce_prod = as_prod or profile == "prod"

    if enforce_prod:
        if settings.get("security.allow_open_dev", False):
            errors.append("security.allow_open_dev must be false in prod")
        if settings.db_type == "memory":
            errors.append("db.type=memory is forbidden in prod")
        if settings.cache_type == "memory":
            errors.append("cache.type=memory is forbidden in prod")
        if settings.broker_type == "memory":
            errors.append("broker.type=memory is forbidden in prod")
        if settings.get("security.tls.mode") != "required":
            errors.append("security.tls.mode must be 'required' in prod")

    if settings.broker_type == "kafka":
        errors.append("broker.type=kafka is reserved and not implemented (doc 11)")

    if settings.db_type not in ("postgres", "memory"):
        errors.append(f"unknown db.type: {settings.db_type}")
    if settings.cache_type not in ("valkey", "memory"):
        errors.append(f"unknown cache.type: {settings.cache_type}")

    # Enabled modules must exist on disk (doc 01 §3).
    import glob
    import os

    present_ids: set[str] = set()
    for prefix in ("", "../../", "../"):
        for path in glob.glob(os.path.join(prefix, "modules", "*", "module.yaml")):
            present_ids.add(os.path.basename(os.path.dirname(path)))
        if present_ids:
            break
    for module_id in settings.enabled_modules:
        if module_id not in present_ids:
            errors.append(f"modules.enabled lists '{module_id}' but no such folder is on disk")

    return errors


def run(args) -> int:
    settings = config.load()
    errors = _rules(settings, as_prod=getattr(args, "as_prod", False))
    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        opzlog.log("doctor", ok=False, errors=errors)
        return 2
    print("doctor: OK")
    opzlog.log("doctor", ok=True)
    return 0
