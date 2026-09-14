/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { Navigate, Outlet } from "react-router-dom";
import { ROUTE_LOGIN } from "@kernel/app/routeConstants";
import { useSession } from "@kernel/auth/SessionStore";

/**
 * Guards authenticated routes and redirects anonymous users to the login page.
 * @returns The nested route outlet or a login redirect.
 */
export function RequireAuth() {
  const { user, loading } = useSession();
  if (loading) return <div className="opz-loading">Loading…</div>;
  if (!user) return <Navigate to={ROUTE_LOGIN} replace />;
  return <Outlet />;
}
