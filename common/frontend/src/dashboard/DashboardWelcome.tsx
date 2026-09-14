/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Dashboard greeting using the signed-in display name.
 */
import { memo } from "react";
import {
  DASHBOARD_WELCOME_FALLBACK,
  DASHBOARD_WELCOME_SUBTITLE,
} from "./dashboardConstants";

interface DashboardWelcomeProps {
  displayName: string;
}

/**
 * Renders the dashboard welcome heading.
 *
 * @param props signed-in display name
 * @returns Welcome title and subtitle
 */
function DashboardWelcomeComponent({ displayName }: DashboardWelcomeProps) {
  const trimmedDisplayName = displayName.trim();
  const welcomeTitle = trimmedDisplayName
    ? `${DASHBOARD_WELCOME_FALLBACK}, ${trimmedDisplayName}`
    : DASHBOARD_WELCOME_FALLBACK;

  return (
    <section className="mmo-dash-welcome">
      <h1 className="mmo-dash-welcome-title">{welcomeTitle}</h1>
      <p className="mmo-dash-welcome-subtitle">{DASHBOARD_WELCOME_SUBTITLE}</p>
    </section>
  );
}

export const DashboardWelcome = memo(DashboardWelcomeComponent);
