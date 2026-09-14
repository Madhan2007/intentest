# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Structured JSON logs with command/module_id/correlation_id (doc 06 §11)."""
from __future__ import annotations

import json
import sys
import time


def log(event: str, **fields) -> None:
    record = {"ts": time.time(), "event": event, **fields}
    print(json.dumps(record), file=sys.stderr)
