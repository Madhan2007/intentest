# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Module plugin loader (doc 05 §6, doc 01 §5). Scans modules/*/module.yaml
present on disk, intersects with platform.yaml modules.enabled, and imports
modules.<id>.python.plugin:register(app, registry) for `python: true` modules.
Identity/admin currently declare python: false, so nothing loads yet."""
from __future__ import annotations

import glob
import importlib
import os
from typing import Any

import yaml


def _present_modules() -> dict[str, dict[str, Any]]:
    present: dict[str, dict[str, Any]] = {}
    for prefix in ("", "../../", "../"):
        for path in glob.glob(os.path.join(prefix, "modules", "*", "module.yaml")):
            with open(path, "r", encoding="utf-8") as f:
                doc = yaml.safe_load(f) or {}
            module_id = doc.get("id")
            if module_id:
                present[module_id] = doc
        if present:
            break
    return present


def discover_modules(app, registry, settings) -> list[str]:
    """Registers each enabled module with `python: true`. Returns the ids that loaded."""
    present = _present_modules()
    loaded: list[str] = []
    for module_id in settings.enabled_modules:
        meta = present.get(module_id)
        if not meta:
            continue
        if not (meta.get("runtimes") or {}).get("python"):
            continue
        try:
            plugin = importlib.import_module(f"modules.{module_id}.python.plugin")
            plugin.register(app, registry)
            loaded.append(module_id)
        except ModuleNotFoundError:
            # Folder declares python: true but has no plugin module — skip, don't crash boot.
            continue
    return loaded
