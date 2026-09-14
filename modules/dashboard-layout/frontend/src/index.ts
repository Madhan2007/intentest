/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Registers dashboard-layout module routes and exports components.
 */
import React from "react";
import type { KernelApp } from "@kernel/kernel/types";
import { DashboardLayoutEditor } from "./pages/DashboardLayoutEditor";

export { DashboardRenderer } from "./components/DashboardRenderer";
export { DashboardLayoutEditor } from "./pages/DashboardLayoutEditor";

export function register(app: KernelApp): void {
  app.addRoutes([
    {
      path: "/admin/dashboard-layout",
      element: React.createElement(DashboardLayoutEditor),
    },
  ]);

  app.addMenuItems([
    {
      id: "dashboard-layout.editor",
      label: "Dashboard Layout",
      path: "/admin/dashboard-layout",
      parent: "admin",
    },
  ]);
}
