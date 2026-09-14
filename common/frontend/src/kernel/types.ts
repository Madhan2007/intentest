/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import type { ReactNode } from "react";

/** One module-contributed route (doc 03 §2, doc 01 §5). */
export interface RouteDef {
  path: string;
  element: ReactNode;
}

/** One module-contributed menu entry. Kernel renders whatever is registered — no app names hardcoded. */
export interface MenuItem {
  id: string;
  label: string;
  path?: string;
  parent?: string | null;
}

/** Facade passed to every module's `register(app)` (doc 01 §5). */
export interface KernelApp {
  addRoutes(routes: RouteDef[]): void;
  addMenuItems(items: MenuItem[]): void;
}

/** Shape every `modules/<id>/frontend/index.ts` must export. */
export interface ModulePlugin {
  register(app: KernelApp): void;
}
