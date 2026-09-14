/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { createBrowserRouter } from "react-router-dom";
import { loadModules } from "@kernel/kernel/module-loader";
import { RequireAuth } from "@kernel/app/guards";
import { AppShell } from "@kernel/app/AppShell";
import { DashboardPage } from "@kernel/app/DashboardPage";
import { NotFoundPage } from "@kernel/app/NotFoundPage";

/**
 * Builds the application router from kernel and module route contributions.
 * @returns The browser router used by the frontend entrypoint.
 */
export function buildRouter() {
  const { routes: moduleRoutes } = loadModules();

  return createBrowserRouter([
    ...moduleRoutes.filter((r) => r.path === "/login"),
    {
      element: <RequireAuth />,
      children: [
        {
          element: <AppShell />,
          children: [
            { path: "/", element: <DashboardPage /> },
            ...moduleRoutes.filter((r) => r.path !== "/login"),
            { path: "*", element: <NotFoundPage /> },
          ],
        },
      ],
    },
  ]);
}
