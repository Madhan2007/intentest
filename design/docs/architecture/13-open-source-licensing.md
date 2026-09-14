# 13 — Open Source Licensing & Reuse

## 1. Intent

The platform, kernel, modules, scripts, and default infrastructure choices are **open source**. Customer solutions reuse the kernel without a proprietary runtime tax. Third-party libraries must be **OSI-approved** and compatible with shipping many customer packs from the same tree.

**Project license (locked):** [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Why Apache-2.0 (not GPL, not a custom EULA):

- Explicit patent grant; suitable for production ERP.
- Permissive reuse: customers and implementers may embed modules in commercial solutions.
- File-level NOTICE keeps attribution when folders are deleted or added (plugin-play).
- Does **not** force customer business data or private solution overlays to be open-sourced.

Each `modules/<id>/` inherits the same license unless that module’s `module.yaml` records a compatible exception (must still be OSI-approved and Apache-2.0-compatible).

## 2. What must be licensed

| Tree | License artifact |
| ---- | ---------------- |
| Repository root | `LICENSE` (Apache-2.0) + `NOTICE` (third-party attributions) |
| `common/` | covered by root LICENSE; no second proprietary license |
| `modules/<id>/` | Apache-2.0; `NOTICE` fragment if the module vendors extra files |
| `docs/` | Apache-2.0 (documentation) |
| Generated maps | same as kernel (build artifacts, not a different license) |
| OCR **models** | PaddleOCR / Tesseract only with OSI or OpenRAIL; pin URL + hash for **high** and **medium** packs in `modules/ocr/scripts` |
| Identity **bio models** | ArcFace/PAD ONNX + **SourceAFIS** (Apache-2.0): OSI or OpenRAIL; pin `identity-models/{high,medium}/` hashes |

Private customer files under `solutions/<id>/branding` and secrets are **data**, not kernel code. They are not required to be open source. Kernel code copied into a customer pack remains Apache-2.0.

## 3. Dependency allow-list (v1 stack)

Only these license families may appear on the production classpath/wheel/image **without legal review**:

| Family | Examples in this architecture |
| ------ | ----------------------------- |
| Apache-2.0 | Spring Boot, OpenCV, Tesseract, PaddleOCR (Apache), **SourceAFIS**, Guava/Caffeine, Tomcat/Netty (check NOTICE). Kafka only if a later drop enables it. |
| MIT | FastAPI, Uvicorn, React, TypeScript, Polars, many Python helpers |
| BSD-2/3 | Nginx, Valkey, PostgreSQL JDBC pieces as applicable, **Flutter SDK** |
| PostgreSQL License | PostgreSQL server |
| ISC | some Node/Python utilities |
| Unicode / OFL | fonts in the SPA (no GPL font packages) |

`opzhubctl doctor license` (design): fail CI if a resolved dependency is GPL-2/3, AGPL, SSPL, BUSL, or missing license, **unless** it is in an isolated worker image that is never linked into the Java kernel **and** the solution manifest opts in after review. Default: **reject strong copyleft in kernel and frontend bundles**.

## 4. Forbidden in default packs

- Proprietary JDKs that disallow production use; use **Eclipse Temurin** or another TCK-certified OpenJDK (GPLv2+CPE for HotSpot is acceptable for the **runtime**, not for redistributing modified kernel source under GPL).
- Closed OCR/face/fingerprint engines (ABBYY, cloud Vision/Rekognition/Face++, proprietary AFIS). Default is Paddle/Tesseract, ArcFace-class ONNX, SourceAFIS.
- Mixing AGPL libraries into Spring or the SPA (would constrain customer reuse).
- Telemetry SDKs that require a commercial license for production volume.

## 5. Reuse rules (plugin-play + license)

1. Deleting a module folder does not change the kernel license.
2. Adding a module requires a `LICENSE` pointer or inheritance from root + a `NOTICE` if it vendors code.
3. Common field kit and DataClient/CacheClient/BrokerClient stay Apache-2.0 so every solution can reuse them.
4. Do not vendor minified third-party code without source and license files in `NOTICE`.
5. SBOM (CycloneDX JSON) is produced at image build; retain with the customer pack.

## 6. Runtime / OS (EC2)

| Component | License posture |
| --------- | --------------- |
| Ubuntu 24.04 | Ubuntu / main archive open packages |
| Docker Engine | Apache-2.0 (Moby) |
| Official images | Prefer Docker Official / Apache-2.0 or BSD bases (`nginx:alpine`, `postgres:16`, `valkey/valkey`) |

## 7. Contributor / NOTICE hygiene (when implementation starts)

- `NOTICE` lists: Spring, FastAPI, Nginx, PostgreSQL, Valkey, OpenCV, PaddleOCR/Tesseract, SourceAFIS, React, Flutter. Kafka only if a later drop vendors it.
- Source headers: `Copyright © {year} ManageMyOpz contributors` + `SPDX-License-Identifier: Apache-2.0`.
- No dual-license traps in kernel (`common/`).

Root `LICENSE` in this repository states Apache-2.0 for the design and future code.
