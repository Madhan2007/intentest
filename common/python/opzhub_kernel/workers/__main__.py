# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Entrypoint for the worker process supervised by opzhub-be-core.
No OCR/image/mail module is packed yet, so this remains idle until shutdown."""
import asyncio

from opzhub_kernel.workers.base import run_all_registered_workers


def main() -> None:
    asyncio.run(run_all_registered_workers())


if __name__ == "__main__":
    main()
