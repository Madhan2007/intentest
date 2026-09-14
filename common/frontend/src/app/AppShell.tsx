/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { Outlet, Link, useLocation } from "react-router-dom";
import { loadModules } from "@kernel/kernel/module-loader";
import { COMPANY_NAME } from "@kernel/brand/brandConstants";
import { UserAccountMenu } from "@kernel/chrome/UserAccountMenu";
import { ROUTE_HOME } from "@kernel/app/routeConstants";

/**
 * Renders the authenticated application shell and registered module menu.
 * The dashboard at home supplies its own header, so shared chrome is skipped there.
 * @returns The shared shell layout for authenticated routes.
 */
export function AppShell() {
  const location = useLocation();
  const { menuItems } = loadModules();

  if (location.pathname === ROUTE_HOME) {
    return <Outlet />;
  }

  return (
    <div className="opz-shell">
      <header className="opz-header">
        <span className="opz-brand">{COMPANY_NAME}</span>
        <span className="opz-spacer" />
        <UserAccountMenu />
      </header>
      <div className="opz-body">
        <nav className="opz-sidebar">
          <Link to={ROUTE_HOME}>Dashboard</Link>
          {menuItems.map((item) => (
            <Link key={item.id} to={item.path ?? ROUTE_HOME}>
              {item.label}
            </Link>
          ))}
        </nav>
        <main className="opz-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
