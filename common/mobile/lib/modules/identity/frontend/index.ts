/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import type { KernelApp } from "@kernel/kernel/types";
import { routes } from "./routes";

/**
 * Registers the identity module frontend routes with the kernel shell.
 * @param app Kernel registration surface for routes and menus.
 * @returns Nothing.
 */
export function register(app: KernelApp): void {
  app.addRoutes(routes);
}
