/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Dashboard header with brand mark, 9-dot launcher, and account menu.
 */
import { memo, useCallback, useRef, useState } from "react";
import { CompanyLogo } from "@kernel/brand/CompanyLogo";
import { UserAccountMenu } from "@kernel/chrome/UserAccountMenu";
import { useDismissOnOutsideEvent } from "@kernel/hooks/useDismissOnOutsideEvent";
import { AppsGridIcon, BellIcon, BELL_ICON_SIZE } from "@kernel/icons/chromeIcons";
import type { ApplicationItem } from "./applicationCatalog";
import { AppsLauncher } from "./AppsLauncher";
import {
  DASHBOARD_APPS_MENU_LABEL,
  DASHBOARD_BRAND_NAME,
  DASHBOARD_NOTIFICATIONS_LABEL,
} from "./dashboardConstants";

interface DashboardHeaderProps {
  launcherApplications: readonly ApplicationItem[];
  isCatalogLoading: boolean;
  catalogError: string | null;
  installingApplicationId: string | null;
  isApplicationDragActive: boolean;
  onInstallApplication: (applicationId: string) => Promise<void>;
  onUninstallApplication: (applicationId: string) => Promise<void>;
  onApplicationDragStart: (applicationId: string) => void;
  onApplicationDragEnd: () => void;
}

/**
 * Renders the Manage My Opz brand, the 9-dot app launcher, and the account menu.
 *
 * @param props launcher and drag bindings
 * @returns The dashboard header bar
 */
function DashboardHeaderComponent({
  launcherApplications,
  isCatalogLoading,
  catalogError,
  installingApplicationId,
  isApplicationDragActive,
  onInstallApplication,
  onUninstallApplication,
  onApplicationDragStart,
  onApplicationDragEnd,
}: DashboardHeaderProps) {
  const [isLauncherOpen, setIsLauncherOpen] = useState(false);
  const launcherRootRef = useRef<HTMLDivElement>(null);

  const closeLauncher = useCallback(() => {
    if (isApplicationDragActive) {
      return;
    }
    setIsLauncherOpen(false);
  }, [isApplicationDragActive]);

  useDismissOnOutsideEvent(isLauncherOpen && !isApplicationDragActive, launcherRootRef, closeLauncher);

  const handleLauncherToggle = useCallback(() => {
    if (isApplicationDragActive) {
      return;
    }
    setIsLauncherOpen((isCurrentlyOpen) => !isCurrentlyOpen);
  }, [isApplicationDragActive]);

  return (
    <header className="mmo-dash-header">
      <div className="mmo-dash-header-inner">
        <div className="mmo-dash-brand">
          <CompanyLogo />
          <span className="mmo-dash-brand-name">{DASHBOARD_BRAND_NAME}</span>
        </div>

        <div className="mmo-dash-header-actions">
          <div
            className={
              isApplicationDragActive
                ? "mmo-dash-launcher-root mmo-dash-launcher-root-dragging"
                : "mmo-dash-launcher-root"
            }
            ref={launcherRootRef}
          >
            <button
              type="button"
              className="mmo-dash-icon-button"
              aria-label={DASHBOARD_APPS_MENU_LABEL}
              title={DASHBOARD_APPS_MENU_LABEL}
              aria-haspopup="dialog"
              aria-expanded={isLauncherOpen}
              onClick={handleLauncherToggle}
            >
              <AppsGridIcon />
            </button>
            {isLauncherOpen ? (
              <AppsLauncher
                applications={launcherApplications}
                isLoading={isCatalogLoading}
                errorMessage={catalogError}
                installingApplicationId={installingApplicationId}
                isApplicationDragActive={isApplicationDragActive}
                onInstall={onInstallApplication}
                onUninstall={onUninstallApplication}
                onApplicationDragStart={onApplicationDragStart}
                onApplicationDragEnd={onApplicationDragEnd}
              />
            ) : null}
          </div>
          <button
            type="button"
            className="mmo-dash-icon-button mmo-dash-icon-button-notify"
            aria-label={DASHBOARD_NOTIFICATIONS_LABEL}
            title={DASHBOARD_NOTIFICATIONS_LABEL}
          >
            <BellIcon size={BELL_ICON_SIZE} />
          </button>
          <UserAccountMenu />
        </div>
      </div>
    </header>
  );
}

export const DashboardHeader = memo(DashboardHeaderComponent);
