// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/material.dart';
import 'package:opzhub_mobile/auth/session.dart';
import 'package:opzhub_mobile/kernel/module_loader.dart';
import 'package:opzhub_mobile/app/app_shell.dart';
import 'package:opzhub_mobile/theme/tokens.dart';

/// Root widget: unauthenticated -> the module-contributed `/login` route;
/// authenticated -> kernel shell. Kernel never hardcodes "identity" — it
/// only looks up the generic route table (doc 01 §5, doc 18 §6).
class OpzApp extends StatefulWidget {
  const OpzApp({super.key});

  @override
  State<OpzApp> createState() => _OpzAppState();
}

class _OpzAppState extends State<OpzApp> {
  final SessionController session = SessionController();

  void _handleSessionChanged() {
    setState(() {});
  }

  @override
  void initState() {
    super.initState();
    session.addListener(_handleSessionChanged);
    session.restore();
  }

  @override
  void dispose() {
    session.removeListener(_handleSessionChanged);
    session.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ManageMyOpz',
      theme: buildOpzTheme(),
      home: _buildHome(),
    );
  }

  Widget _buildHome() {
    if (session.loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (session.user == null) {
      final loginRoute = loadModules().routes.where((r) => r.path == '/login').firstOrNull;
      if (loginRoute != null) {
        return _SessionScope(session: session, child: Builder(builder: loginRoute.builder));
      }
      return const Scaffold(body: Center(child: Text('No login screen registered.')));
    }
    return _SessionScope(session: session, child: AppShell(session: session));
  }
}

/// Exposes [SessionController] to descendant module widgets without a
/// third-party state-management dependency (doc 03 §14).
class _SessionScope extends InheritedNotifier<SessionController> {
  const _SessionScope({required SessionController session, required super.child})
      : super(notifier: session);

  static SessionController of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<_SessionScope>();
    assert(scope != null, 'No SessionController found in context');
    return scope!.notifier!;
  }
}

/// Returns the current [SessionController] from the widget tree.
SessionController sessionOf(BuildContext context) => _SessionScope.of(context);
