/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Three-dot overflow menu for a dashboard application card.
 */
import { memo, useCallback, useRef, useState } from "react";
import { useDismissOnOutsideEvent } from "@kernel/hooks/useDismissOnOutsideEvent";
import { MENU_ICON_SIZE, MoreHorizontalIcon, UninstallIcon } from "@kernel/icons/chromeIcons";
import {
  DASHBOARD_CARD_MENU_LABEL,
  DASHBOARD_LAUNCHER_UNINSTALL_LABEL,
} from "./dashboardConstants";

interface ApplicationCardMenuProps {
  applicationName: string;
  onUninstall: () => void;
}

/**
 * Renders a 3-dot trigger with uninstall inside the dropdown.
 *
 * @param props application name and uninstall handler
 * @returns card overflow menu
 */
function ApplicationCardMenuComponent({ applicationName, onUninstall }: ApplicationCardMenuProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRootRef = useRef<HTMLDivElement>(null);
  const menuTriggerLabel = `${DASHBOARD_CARD_MENU_LABEL} for ${applicationName}`;

  const closeMenu = useCallback(() => {
    setIsMenuOpen(false);
  }, []);

  useDismissOnOutsideEvent(isMenuOpen, menuRootRef, closeMenu);

  const handleTriggerClick = useCallback(() => {
    setIsMenuOpen((isCurrentlyOpen) => !isCurrentlyOpen);
  }, []);

  const handleUninstallClick = useCallback(() => {
    closeMenu();
    onUninstall();
  }, [closeMenu, onUninstall]);

  return (
    <div className="mmo-dash-card-menu-root" ref={menuRootRef}>
      <button
        type="button"
        className="mmo-dash-card-menu"
        aria-label={menuTriggerLabel}
        title={DASHBOARD_CARD_MENU_LABEL}
        aria-haspopup="menu"
        aria-expanded={isMenuOpen}
        onClick={handleTriggerClick}
      >
        <MoreHorizontalIcon size={MENU_ICON_SIZE} />
      </button>
      {isMenuOpen ? (
        <div className="mmo-dash-card-menu-panel" role="menu">
          <button
            type="button"
            className="mmo-dash-card-menu-item mmo-dash-card-menu-item-danger"
            role="menuitem"
            onClick={handleUninstallClick}
          >
            <UninstallIcon size={MENU_ICON_SIZE} />
            <span>{DASHBOARD_LAUNCHER_UNINSTALL_LABEL}</span>
          </button>
        </div>
      ) : null}
    </div>
  );
}

export const ApplicationCardMenu = memo(ApplicationCardMenuComponent);
