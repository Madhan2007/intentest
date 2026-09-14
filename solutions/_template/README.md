# Solution template — copy this folder per customer (doc 02 §9, doc 08 §3).
See [solution.manifest.yaml](solution.manifest.yaml) for the enabled module list.

- `branding/` — logo, theme override, optional `rich/` art (ignored when `gui.mode: lite`)
- `config/platform.override.yaml` — db/cache/broker types, urls, per-customer overrides
- `config/secrets.env.example` — documents required secret keys (never commit real secrets)
