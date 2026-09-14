# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Same merged platform.yaml as the Java and Python kernels (doc 06 §4, doc 08 §1)."""
from __future__ import annotations

import sys
from pathlib import Path

# Reuse opzhub_kernel's loader rather than duplicating placeholder/merge logic.
_PYTHON_KERNEL = Path(__file__).resolve().parents[2] / "python"
if str(_PYTHON_KERNEL) not in sys.path:
    sys.path.insert(0, str(_PYTHON_KERNEL))

from opzhub_kernel.settings import Settings  # noqa: E402


def load() -> Settings:
    return Settings()
