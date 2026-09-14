# Generators (doc 02 §10)

Two build-time generators regenerate artifacts from `modules/*/module.yaml`
so nobody hand-edits them:

| Generator | What it writes | Run via |
| --------- | --------------- | ------- |
| Module maps (web / Java / Python / Flutter) | `common/*/generated/module-map.*`, pom.xml build-helper source list, Flutter `lib/generated/module_map.dart` | `opzhubctl module-gen` (`common/scripts/opzhub_scripts/commands/module_gen.py`) |
| Nginx API locations | Extra `location /api/v1/opzhub/<prefix>/` blocks for enabled modules' `provides.api_prefixes` | `python tools/gen_nginx_locations.py` (this folder) |

`opzhubctl module-gen` is the canonical entrypoint for language-specific
maps; `tools/` holds generators that are not part of that per-language set
(today: the Nginx location list referenced in doc 09 §4.1).
