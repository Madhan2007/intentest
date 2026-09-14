/// BUILD ARTIFACT — regenerate with `opzhubctl module-gen` (doc 01 §5). Do not hand-edit.
library;

/// `lib/modules` is a directory junction to the top-level `modules/` folder
/// (doc 02 §8) — Dart cannot resolve relative imports that escape the pub
/// package root, so the module tree is made reachable under `lib/` instead
/// of copying files.
import 'package:opzhub_mobile/modules/identity/mobile/plugin.dart' as identity;
import 'package:opzhub_mobile/modules/admin/mobile/plugin.dart' as admin;
import 'package:opzhub_mobile/kernel/types.dart';

final List<ModulePlugin> modulePlugins = [
  identity.IdentityPlugin(),
  admin.AdminPlugin(),
];
