# Standalone Development Help

Run ManageMyOpz directly on the workstation without Docker. This is the fastest
feedback loop for application development. Java and Python use the development
platform configuration, where `db.type` and `cache.type` default to `memory`.

## Prerequisites

- JDK 21 and Maven 3.9+
- Node.js 20+
- Python 3.12+
- Flutter SDK only when testing the mobile client

Run all commands from the repository root unless a command changes directory.
Use a separate terminal for each long-running service.

## 1. Start the Java application backend

```powershell
Set-Location common\backend
mvn spring-boot:run
```

The `opzhub-be-app` application listens on `http://localhost:8114`.

Health check:

```powershell
curl http://localhost:8114/api/v1/opzhub/health
```

Stop it with `Ctrl+C` in its terminal.

## 2. Start the React web application

```powershell
Set-Location common\frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api/v1/opzhub` to port `8114`
and `/api/v1/ai` to port `8117`.

Stop it with `Ctrl+C` in its terminal.

## 3. Start the Python core API

The Python API is optional for the current kernel pack, but start it when
working on AI, OCR, mail, or Python health behavior.

```powershell
Set-Location common\python
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install fastapi "uvicorn[standard]" pyyaml pydantic
uvicorn services.ai.main:app --reload --port 8117
```

Verify it:

```powershell
curl http://localhost:8117/api/v1/ai/health
```

Stop it with `Ctrl+C` in its terminal.

## 4. Start the Flutter client

Use the directly running backend origin:

```powershell
Set-Location common\mobile
flutter pub get
flutter run --dart-define=OPZHUB_ORIGIN=http://localhost:8114
```

Stop it with `q` in the Flutter terminal or close the target device/app.

## 5. Run the command-line checks

```powershell
Set-Location common\scripts
python opzhubctl doctor
```

## Development behavior

- No public Nginx gateway runs in standalone mode.
- HTTP is local-only development traffic; use Docker/EC2 for HTTPS gateway
  testing.
- The memory database/cache are cleared when the Java application stops.
- No default login credentials are provided. User provisioning must use the
  identity-management flow when it is implemented.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Port `8114` unavailable | Stop the previous Java process or change its configured port. |
| Web API requests fail | Ensure the Java backend is running on `8114`. |
| AI proxy requests fail | Start the Python API on `8117`; it is optional by default. |
| Flutter cannot connect | Confirm the Dart define uses the backend URL reachable by the device. |
| Java configuration error | Run `python common/scripts/opzhubctl doctor`. |
