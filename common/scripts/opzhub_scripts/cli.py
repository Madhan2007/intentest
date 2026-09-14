# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Root argparse parser (doc 06 §4). Module-specific subcommands are
discovered from modules/*/scripts/cli.yaml (doc 06 §5) when present."""
from __future__ import annotations

import argparse
import glob
import os
import sys

from opzhub_scripts.commands import compose as cmd_compose
from opzhub_scripts.commands import doctor as cmd_doctor
from opzhub_scripts.commands import health as cmd_health
from opzhub_scripts.commands import migrate as cmd_migrate
from opzhub_scripts.commands import module_gen as cmd_module_gen


def _discover_module_commands() -> dict[str, dict]:
    """modules/<feature>/scripts/cli.yaml -> {command_name: {module, entry, help}} (doc 06 §5)."""
    import yaml

    discovered: dict[str, dict] = {}
    for prefix in ("", "../../", "../"):
        found = glob.glob(os.path.join(prefix, "modules", "*", "scripts", "cli.yaml"))
        if not found:
            continue
        for path in found:
            with open(path, "r", encoding="utf-8") as f:
                doc = yaml.safe_load(f) or {}
            module_id = doc.get("id")
            for command in doc.get("commands", []):
                name = command.get("name")
                if name:
                    discovered[f"{module_id}-{name}"] = {"module": module_id, **command, "base_dir": os.path.dirname(path)}
        break
    return discovered


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="opzhubctl", description="ManageMyOpz kernel data verbs")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("health", help="ping nginx/erp/ai/db/cache/broker")

    doctor_p = sub.add_parser("doctor", help="validate the merged platform config")
    doctor_p.add_argument("--as-prod", action="store_true", dest="as_prod")

    sub.add_parser("migrate", help="apply pending kernel + module SQL migrations")
    sub.add_parser("module-gen", help="regenerate build-time module maps")

    compose_p = sub.add_parser("compose", help="docker compose wrapper")
    compose_p.add_argument("action", choices=["up", "down", "logs"])
    compose_p.add_argument("service", nargs="?")
    compose_p.add_argument("--dev", action="store_true")

    for key, meta in _discover_module_commands().items():
        help_text = meta.get("help", "")
        module_p = sub.add_parser(key, help=f"[{meta['module']}] {help_text}")
        for arg in meta.get("args", []):
            module_p.add_argument(f"--{arg['name']}", required=bool(arg.get("required")))

    return parser


def _run_module_command(name: str, args: argparse.Namespace) -> int:
    meta = _discover_module_commands()[name]
    entry = meta["entry"]  # "commands/seed_coa.py:run"
    file_part, func_name = entry.split(":")
    module_path = os.path.join(meta["base_dir"], file_part)
    import importlib.util

    spec = importlib.util.spec_from_file_location(f"opzhub_module_cmd_{name}", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    func = getattr(module, func_name)
    return int(func(args) or 0)


def main(argv: list[str]) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    dispatch = {
        "health": cmd_health.run,
        "doctor": cmd_doctor.run,
        "migrate": cmd_migrate.run,
        "module-gen": cmd_module_gen.run,
        "compose": cmd_compose.run,
    }
    handler = dispatch.get(args.command)
    if handler:
        return handler(args)
    return _run_module_command(args.command, args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
