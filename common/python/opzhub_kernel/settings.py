# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Loads the same platform.yaml Java reads (doc 08 §1), with the same merge
order: platform.yaml -> solution override -> /etc/opzhub override -> env vars.
"""
from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any

import yaml

_PLACEHOLDER = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(:([^}]*))?\}")

_ROOT_PREFIXES = ["", "../../", "../"]


def _resolve_placeholders(value: Any) -> Any:
    if isinstance(value, str):
        def _sub(match: re.Match[str]) -> str:
            var_name, _, default = match.groups()
            return os.environ.get(var_name, default if default is not None else "")
        return _PLACEHOLDER.sub(_sub, value)
    if isinstance(value, dict):
        return {k: _resolve_placeholders(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_resolve_placeholders(v) for v in value]
    return value


def _find_first_existing(relative_path: str) -> Path | None:
    for prefix in _ROOT_PREFIXES:
        candidate = Path(prefix + relative_path)
        if candidate.is_file():
            return candidate
    return None


def _deep_merge(base: dict, override: dict) -> dict:
    result = dict(base)
    for key, value in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = _deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def load_settings() -> dict[str, Any]:
    """Merged, placeholder-resolved platform configuration (doc 08 §1)."""
    explicit = os.environ.get("PLATFORM_CONFIG_PATH")
    base_path = Path(explicit) if explicit and Path(explicit).is_file() else _find_first_existing(
        "platform/config/platform.yaml"
    )
    if base_path is None:
        raise FileNotFoundError("platform.yaml not found (set PLATFORM_CONFIG_PATH)")

    with open(base_path, "r", encoding="utf-8") as f:
        merged: dict[str, Any] = yaml.safe_load(f) or {}

    solution_id = (merged.get("solution") or {}).get("id")
    if solution_id:
        override_path = _find_first_existing(f"solutions/{solution_id}/config/platform.override.yaml")
        if override_path:
            with open(override_path, "r", encoding="utf-8") as f:
                merged = _deep_merge(merged, yaml.safe_load(f) or {})

    site_override = Path("/etc/opzhub/platform.override.yaml")
    if site_override.is_file():
        with open(site_override, "r", encoding="utf-8") as f:
            merged = _deep_merge(merged, yaml.safe_load(f) or {})

    return _resolve_placeholders(merged)


class Settings:
    """Thin typed accessor over the merged YAML — mirrors Java PlatformProperties."""

    def __init__(self, raw: dict[str, Any] | None = None) -> None:
        self.raw = raw if raw is not None else load_settings()

    def get(self, dotted_path: str, default: Any = None) -> Any:
        node: Any = self.raw
        for part in dotted_path.split("."):
            if not isinstance(node, dict) or part not in node:
                return default
            node = node[part]
        return node

    @property
    def db_type(self) -> str:
        return self.get("db.type", "memory")

    @property
    def cache_type(self) -> str:
        return self.get("cache.type", "memory")

    @property
    def broker_type(self) -> str:
        return self.get("broker.type", "memory")

    @property
    def enabled_modules(self) -> list[str]:
        return self.get("modules.enabled", []) or []
