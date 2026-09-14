/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import type { RouteDef } from "@kernel/kernel/types";
import { LoginPage } from "./pages/LoginPage";

/** Login route contributions for the identity frontend module. */
export const routes: RouteDef[] = [{ path: "/login", element: <LoginPage /> }];
