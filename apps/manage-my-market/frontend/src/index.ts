/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Plugin entrypoint for Manage My Market frontend.
 */
import type { KernelApp } from "@kernel/kernel/types";
import { MARKET_ROUTES } from "./routes";
import { MARKET_MENU_ITEMS } from "./menu";

export function register(app: KernelApp): void {
  app.addRoutes(MARKET_ROUTES);
  app.addMenuItems(MARKET_MENU_ITEMS);
}
