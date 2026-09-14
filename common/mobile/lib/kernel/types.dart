// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:flutter/widgets.dart';

/// One module-contributed route (doc 03 §14, doc 01 §5).
class RouteDef {
  /// Route path contributed by a module.
  final String path;

  /// Builder used when the route is rendered.
  final WidgetBuilder builder;

  /// Creates a module route definition.
  const RouteDef({required this.path, required this.builder});
}

/// One module-contributed menu entry. Kernel renders whatever is registered.
class MenuItem {
  /// Stable menu item identifier.
  final String id;

  /// User-facing menu label.
  final String label;

  /// Optional route path associated with the item.
  final String? path;

  /// Creates a module menu contribution.
  const MenuItem({required this.id, required this.label, this.path});
}

/// Facade passed to every module's `register(ctx)` (doc 01 §5).
abstract class KernelApp {
  /// Adds module-defined routes to the kernel route table.
  void addRoutes(List<RouteDef> routes);

  /// Adds module-defined menu items to the kernel shell.
  void addMenuItems(List<MenuItem> items);
}

/// Shape every `modules/<id>/mobile/plugin.dart` must expose.
abstract class ModulePlugin {
  /// Registers a module's mobile contributions with the kernel app.
  void register(KernelApp app);
}
