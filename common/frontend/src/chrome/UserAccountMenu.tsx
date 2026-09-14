/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Signed-in account menu reused by the dashboard header and app shell.
 */
import { memo, useCallback, useMemo, useRef, useState } from "react";
import { useSessionActions, useSessionUser } from "@kernel/auth/SessionStore";
import {
  ACCOUNT_MENU_FALLBACK_NAME,
  ACCOUNT_MENU_SIGN_OUT_LABEL,
  ACCOUNT_MENU_TRIGGER_LABEL,
} from "@kernel/chrome/accountMenuConstants";
import { initialsFromDisplayName } from "@kernel/chrome/initialsFromDisplayName";
import { useDismissOnOutsideEvent } from "@kernel/hooks/useDismissOnOutsideEvent";
import { MENU_ICON_SIZE, MoreVerticalIcon } from "@kernel/icons/chromeIcons";
import "@kernel/css/account-menu.css";

/**
 * Renders the current user name and a sign-out menu.
 * Logout only clears the session; RequireAuth sends the user to login so this
 * tree is not navigated after unmount.
 * @returns The account trigger and dropdown
 */
function UserAccountMenuComponent() {
  const sessionUser = useSessionUser();
  const { logout } = useSessionActions();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRootRef = useRef<HTMLDivElement>(null);
  const displayName = sessionUser?.n?.trim() || ACCOUNT_MENU_FALLBACK_NAME;
  const avatarInitials = useMemo(
    () => initialsFromDisplayName(displayName),
    [displayName]
  );

  const closeMenu = useCallback(() => {
    setIsMenuOpen(false);
  }, []);

  useDismissOnOutsideEvent(isMenuOpen, menuRootRef, closeMenu);

  const handleTriggerClick = useCallback(() => {
    setIsMenuOpen((isCurrentlyOpen) => !isCurrentlyOpen);
  }, []);

  const handleSignOut = useCallback(async () => {
    closeMenu();
    await logout();
  }, [closeMenu, logout]);

  return (
    <div className="mmo-account" ref={menuRootRef}>
      <button
        type="button"
        className="mmo-account-trigger"
        aria-label={ACCOUNT_MENU_TRIGGER_LABEL}
        aria-haspopup="menu"
        aria-expanded={isMenuOpen}
        onClick={handleTriggerClick}
      >
        <span className="mmo-account-avatar">{avatarInitials}</span>
        <span className="mmo-account-name">{displayName}</span>
        <MoreVerticalIcon size={MENU_ICON_SIZE} />
      </button>
      {isMenuOpen ? (
        <div className="mmo-account-menu" role="menu">
          <button
            type="button"
            className="mmo-account-menu-item"
            role="menuitem"
            onClick={handleSignOut}
          >
            {ACCOUNT_MENU_SIGN_OUT_LABEL}
          </button>
        </div>
      ) : null}
    </div>
  );
}

export const UserAccountMenu = memo(UserAccountMenuComponent);
