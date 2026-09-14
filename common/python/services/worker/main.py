# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Entrypoint: `python -m services.worker.main` (doc 05 §3). Runs the
worker process in `opzhub-be-core` — consumes the broker and executes pipelines.
No OCR/image/mail module is packed yet, so run_all_registered_workers() is
a no-op until one is."""
import asyncio

from opzhub_kernel.workers.base import run_all_registered_workers


def main() -> None:
    asyncio.run(run_all_registered_workers())


if __name__ == "__main__":
    main()
