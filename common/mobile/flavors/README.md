# Build flavors (doc 02 §3, doc 17 §6)

Full native flavor wiring (Android `productFlavors` in `android/app/build.gradle`,
iOS schemes/xcconfig) is **not set up yet** — Android SDK licenses are not
accepted in this environment and native flavor config is a deeper, per-platform
change than this pass covers.

For now, environment selection uses `--dart-define=OPZHUB_ORIGIN=<url>` (see
`lib/api/http_client.dart`), matching the dev command in doc 17 §3:

```bash
flutter run --dart-define=OPZHUB_ORIGIN=http://10.0.2.2:8114   # Android emulator
flutter run --dart-define=OPZHUB_ORIGIN=http://localhost:8114  # iOS simulator / desktop / web
```

Add real `dev` / `staging` / `prod` Android+iOS flavors here once store builds
are needed (doc 17 §6 "Store listings").
