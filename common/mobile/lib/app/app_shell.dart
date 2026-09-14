// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/material.dart';
import 'package:opzhub_mobile/auth/session.dart';
import 'package:opzhub_mobile/kernel/module_loader.dart';
import 'package:opzhub_mobile/app/dashboard_page.dart';

/// Kernel chrome: drawer built from registered menu items only (doc 03 §2, §14).
class AppShell extends StatelessWidget {
  final SessionController session;
  const AppShell({super.key, required this.session});

  @override
  Widget build(BuildContext context) {
    final loaded = loadModules();
    return Scaffold(
      appBar: AppBar(
        title: const Text('ManageMyOpz'),
        actions: [
          if (session.user != null)
            IconButton(
              icon: const Icon(Icons.logout),
              tooltip: 'Sign out',
              onPressed: () => session.logout(),
            ),
        ],
      ),
      drawer: Drawer(
        child: ListView(
          children: [
            const DrawerHeader(child: Text('Menu')),
            ListTile(title: const Text('Dashboard'), onTap: () => Navigator.pop(context)),
            for (final item in loaded.menuItems)
              ListTile(title: Text(item.label), onTap: () => Navigator.pop(context)),
          ],
        ),
      ),
      body: const DashboardPage(),
    );
  }
}
