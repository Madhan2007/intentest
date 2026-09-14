/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: 9-dot launcher as icon tiles with install and uninstall actions.
 */
import { memo, useCallback, useMemo } from "react";
import type { ApplicationItem } from "./applicationCatalog";
import {
  DASHBOARD_LAUNCHER_LOAD_ERROR,
  DASHBOARD_LAUNCHER_LOADING,
  DASHBOARD_LAUNCHER_TITLE,
} from "./dashboardConstants";
import { LauncherTile } from "./LauncherTile";
import { sortLauncherApplications } from "./sortLauncherApplications";

interface AppsLauncherProps {
  applications: readonly ApplicationItem[];
  isLoading: boolean;
  errorMessage: string | null;
  installingApplicationId: string | null;
  isApplicationDragActive: boolean;
  onInstall: (applicationId: string) => Promise<void>;
  onUninstall: (applicationId: string) => Promise<void>;
  onApplicationDragStart: (applicationId: string) => void;
  onApplicationDragEnd: () => void;
}

/**
 * Renders catalog icons only. Description appears on hover.
 *
 * @param props launcher catalog, install, uninstall, and drag bindings
 * @returns Apps launcher panel
 */
function AppsLauncherComponent({
  applications,
  isLoading,
  errorMessage,
  installingApplicationId,
  isApplicationDragActive,
  onInstall,
  onUninstall,
  onApplicationDragStart,
  onApplicationDragEnd,
}: AppsLauncherProps) {
  const orderedApplications = useMemo(
    () => sortLauncherApplications(applications),
    [applications]
  );

  const handleInstall = useCallback(
    (applicationId: string) => {
      void onInstall(applicationId);
    },
    [onInstall]
  );

  const handleUninstall = useCallback(
    (applicationId: string) => {
      void onUninstall(applicationId);
    },
    [onUninstall]
  );

  return (
    <div
      className={
        isApplicationDragActive
          ? "mmo-dash-launcher mmo-dash-launcher-dragging"
          : "mmo-dash-launcher"
      }
      role="dialog"
      aria-label={DASHBOARD_LAUNCHER_TITLE}
    >
      {errorMessage ? (
        <p className="mmo-dash-launcher-error">{errorMessage || DASHBOARD_LAUNCHER_LOAD_ERROR}</p>
      ) : null}
      {isLoading && applications.length === 0 ? (
        <p className="mmo-dash-launcher-empty">{DASHBOARD_LAUNCHER_LOADING}</p>
      ) : (
        <ul className="mmo-dash-launcher-grid">
          {orderedApplications.map((application) => {
            const isInstalling = installingApplicationId === application.id;
            return (
              <LauncherTile
                key={application.id}
                application={application}
                isInstalling={isInstalling}
                onInstall={handleInstall}
                onUninstall={handleUninstall}
                onApplicationDragStart={onApplicationDragStart}
                onApplicationDragEnd={onApplicationDragEnd}
              />
            );
          })}
        </ul>
      )}
    </div>
  );
}

export const AppsLauncher = memo(AppsLauncherComponent);
