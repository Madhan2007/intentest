/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: One 9-dot launcher tile with install or uninstall actions.
 */
import { memo, useCallback, useMemo, type DragEvent } from "react";
import { InstallIcon, MENU_ICON_SIZE, UninstallIcon } from "@kernel/icons/chromeIcons";
import type { ApplicationItem } from "./applicationCatalog";
import { writeDraggedApplicationId } from "./applicationDrag";
import { ApplicationGlyph } from "./ApplicationGlyph";
import {
  DASHBOARD_LAUNCHER_INSTALL_LABEL,
  DASHBOARD_LAUNCHER_UNINSTALL_LABEL,
  LICENSE_STATE_INSTALLED,
  LICENSE_STATE_LICENSED,
  LICENSE_STATE_UNLICENSED,
} from "./dashboardConstants";
import { InstallProgressBar } from "./InstallProgressBar";
import { licensedLauncherIconColor } from "./mixHexColor";

interface LauncherTileProps {
  application: ApplicationItem;
  isInstalling: boolean;
  onInstall: (applicationId: string) => void;
  onUninstall: (applicationId: string) => void;
  onApplicationDragStart: (applicationId: string) => void;
  onApplicationDragEnd: () => void;
}

/**
 * Renders one launcher icon, optional action, and install progress.
 *
 * @param props catalog item and launcher actions
 * @returns launcher tile
 */
function LauncherTileComponent({
  application,
  isInstalling,
  onInstall,
  onUninstall,
  onApplicationDragStart,
  onApplicationDragEnd,
}: LauncherTileProps) {
  const isLicensed = application.licenseState === LICENSE_STATE_LICENSED;
  const isInstalled = application.licenseState === LICENSE_STATE_INSTALLED;
  const canDrag = isLicensed && !isInstalling;
  const tooltip = `${application.name}. ${application.description}`;
  const iconStyle = useMemo(
    () => launcherIconStyle(application.licenseState, application.iconBackgroundColor),
    [application.iconBackgroundColor, application.licenseState]
  );

  const handleInstallClick = useCallback(() => {
    onInstall(application.id);
  }, [application.id, onInstall]);

  const handleUninstallClick = useCallback(() => {
    onUninstall(application.id);
  }, [application.id, onUninstall]);

  const handleDragStart = useCallback(
    (dragEvent: DragEvent<HTMLDivElement>) => {
      if (!isLicensed) {
        dragEvent.preventDefault();
        return;
      }
      writeDraggedApplicationId(dragEvent.dataTransfer, application.id);
      onApplicationDragStart(application.id);
    },
    [application.id, isLicensed, onApplicationDragStart]
  );

  return (
    <li className={launcherTileClassName(application.licenseState)}>
      <div
        className="mmo-dash-launcher-tile-icon"
        style={iconStyle}
        title={tooltip}
        aria-label={tooltip}
        draggable={canDrag}
        onDragStart={isLicensed ? handleDragStart : undefined}
        onDragEnd={isLicensed ? onApplicationDragEnd : undefined}
      >
        <ApplicationGlyph iconId={application.iconId} />
      </div>
      {isLicensed ? (
        <button
          type="button"
          className="mmo-dash-launcher-install"
          aria-label={`${DASHBOARD_LAUNCHER_INSTALL_LABEL} ${application.name}`}
          title={DASHBOARD_LAUNCHER_INSTALL_LABEL}
          disabled={isInstalling}
          draggable={false}
          onClick={handleInstallClick}
        >
          <InstallIcon size={MENU_ICON_SIZE} />
        </button>
      ) : null}
      {isInstalled ? (
        <button
          type="button"
          className="mmo-dash-launcher-uninstall"
          aria-label={`${DASHBOARD_LAUNCHER_UNINSTALL_LABEL} ${application.name}`}
          title={DASHBOARD_LAUNCHER_UNINSTALL_LABEL}
          draggable={false}
          onClick={handleUninstallClick}
        >
          <UninstallIcon size={MENU_ICON_SIZE} />
        </button>
      ) : null}
      {isInstalling ? (
        <InstallProgressBar
          trackClassName="mmo-dash-launcher-progress"
          barClassName="mmo-dash-launcher-progress-bar"
        />
      ) : null}
    </li>
  );
}

function launcherIconStyle(
  licenseState: ApplicationItem["licenseState"],
  iconBackgroundColor: string
): { backgroundColor: string } | undefined {
  if (licenseState === LICENSE_STATE_UNLICENSED) {
    return undefined;
  }
  if (licenseState === LICENSE_STATE_LICENSED) {
    return { backgroundColor: licensedLauncherIconColor(iconBackgroundColor) };
  }
  return { backgroundColor: iconBackgroundColor };
}

function launcherTileClassName(licenseState: ApplicationItem["licenseState"]): string {
  if (licenseState === LICENSE_STATE_INSTALLED) {
    return "mmo-dash-launcher-tile mmo-dash-launcher-tile-installed";
  }
  if (licenseState === LICENSE_STATE_LICENSED) {
    return "mmo-dash-launcher-tile mmo-dash-launcher-tile-licensed";
  }
  return "mmo-dash-launcher-tile mmo-dash-launcher-tile-unlicensed";
}

export const LauncherTile = memo(LauncherTileComponent);
