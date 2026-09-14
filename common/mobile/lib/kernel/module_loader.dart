// Organization: Technosprint info Solutions
// Owner: Logaraj S
// Created at: 2026-09-01
// Description: Governed by ManageMyOpz Flutter coding standards.
import 'package:opzhub_mobile/generated/module_map.dart';
import 'package:opzhub_mobile/kernel/types.dart';

/// Result of calling `register()` on every module in the generated map (doc 01 §5).
class LoadedModules {
  /// All routes contributed by loaded modules.
  final List<RouteDef> routes;

  /// All menu items contributed by loaded modules.
  final List<MenuItem> menuItems;

  /// Creates the aggregated module contribution set.
  const LoadedModules({required this.routes, required this.menuItems});
}

class _KernelAppImpl implements KernelApp {
  final List<RouteDef> routes = [];
  final List<MenuItem> menuItems = [];

  @override
  void addRoutes(List<RouteDef> newRoutes) => routes.addAll(newRoutes);

  @override
  void addMenuItems(List<MenuItem> items) => menuItems.addAll(items);
}

/// Loads all generated mobile module plugins into a single kernel view.
LoadedModules loadModules() {
  final app = _KernelAppImpl();
  for (final plugin in modulePlugins) {
    plugin.register(app);
  }
  return LoadedModules(routes: app.routes, menuItems: app.menuItems);
}
