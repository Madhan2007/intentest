/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Calls register(app) on every module in the generated map.
 */
import type { KernelApp, MenuItem, RouteDef } from "@kernel/kernel/types";
import { MODULES } from "@kernel/generated/module-map";

interface LoadedModules {
  routes: RouteDef[];
  menuItems: MenuItem[];
}

let loadedModules: LoadedModules | null = null;

/**
 * Registers every packed module once, then reuses the same route and menu lists.
 *
 * @returns Cached module routes and menu items
 */
export function loadModules(): LoadedModules {
  if (loadedModules !== null) {
    return loadedModules;
  }

  const routes: RouteDef[] = [];
  const menuItems: MenuItem[] = [];
  const app: KernelApp = {
    addRoutes: (moduleRoutes) => routes.push(...moduleRoutes),
    addMenuItems: (moduleMenuItems) => menuItems.push(...moduleMenuItems),
  };

  for (const packedModule of MODULES) {
    packedModule.register(app);
  }

  loadedModules = { routes, menuItems };
  return loadedModules;
}
