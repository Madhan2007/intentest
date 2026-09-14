// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:opzhub_mobile/kernel/types.dart';
import 'pages/login_page.dart';

/// Registers the login route only — session state lives in the kernel
/// (doc 03 §2 "Auth UI", doc 01 §5).
class IdentityPlugin implements ModulePlugin {
  @override
  void register(KernelApp app) {
    app.addRoutes([
      RouteDef(path: '/login', builder: (context) => const LoginPage()),
    ]);
  }
}
