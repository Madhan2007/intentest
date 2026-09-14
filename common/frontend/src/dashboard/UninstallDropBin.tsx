/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Floating dustbin shown while an application card is held.
 */
import { memo } from "react";
import { UninstallIcon } from "@kernel/icons/chromeIcons";
import {
  DASHBOARD_UNINSTALL_BIN_LABEL,
  UNINSTALL_BIN_ICON_SIZE,
  UNINSTALL_FLOATING_BIN_HEIGHT,
  UNINSTALL_FLOATING_BIN_WIDTH,
} from "./dashboardConstants";
import type { FloatingUninstallBinPosition } from "./floatingUninstallBin";

interface UninstallDropBinProps {
  position: FloatingUninstallBinPosition;
  isUninstallBinHot: boolean;
}

/**
 * Renders a floating uninstall dustbin next to the held card.
 *
 * @param props viewport position and hover state
 * @returns floating uninstall bin
 */
function UninstallDropBinComponent({ position, isUninstallBinHot }: UninstallDropBinProps) {
  return (
    <div
      className={
        isUninstallBinHot
          ? "mmo-dash-uninstall-bin mmo-dash-uninstall-bin-float mmo-dash-uninstall-bin-active"
          : "mmo-dash-uninstall-bin mmo-dash-uninstall-bin-float"
      }
      style={{
        top: position.top,
        left: position.left,
        width: UNINSTALL_FLOATING_BIN_WIDTH,
        height: UNINSTALL_FLOATING_BIN_HEIGHT,
      }}
      aria-label={DASHBOARD_UNINSTALL_BIN_LABEL}
    >
      <UninstallIcon size={UNINSTALL_BIN_ICON_SIZE} />
    </div>
  );
}

export const UninstallDropBin = memo(UninstallDropBinComponent);
