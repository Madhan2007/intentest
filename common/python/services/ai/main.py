# Organization: Technosprint info Solutions
# Owner: Logaraj S
# Created at: 2026-09-01
# Description: Governed by ManageMyOpz Python coding standards.
"""Entrypoint: `uvicorn services.ai.main:app` (doc 05 §3). Runs the async
API process (`opzhub-be-core` / `opzpy`); heavy pipelines run in services/worker."""
from opzhub_kernel.app_factory import create_app

app = create_app()
