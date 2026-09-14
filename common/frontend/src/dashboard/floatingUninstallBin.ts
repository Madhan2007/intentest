/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Positions the floating uninstall bin near a held application card.
 */
import {
  UNINSTALL_FLOATING_BIN_HEIGHT,
  UNINSTALL_FLOATING_BIN_OFFSET_PX,
  UNINSTALL_FLOATING_BIN_VIEWPORT_PADDING,
  UNINSTALL_FLOATING_BIN_WIDTH,
} from "./dashboardConstants";

export interface FloatingUninstallBinPosition {
  applicationId: string;
  top: number;
  left: number;
}

/**
 * Places the floating dustbin just below the held card, or above if needed.
 *
 * @param cardRect held application card bounds
 * @returns fixed viewport coordinates
 */
export function floatingBinPositionFromCard(cardRect: DOMRect): { top: number; left: number } {
  const viewportPadding = UNINSTALL_FLOATING_BIN_VIEWPORT_PADDING;
  const centeredLeft = cardRect.left + cardRect.width / 2 - UNINSTALL_FLOATING_BIN_WIDTH / 2;
  const left = Math.min(
    window.innerWidth - UNINSTALL_FLOATING_BIN_WIDTH - viewportPadding,
    Math.max(viewportPadding, centeredLeft)
  );
  const topBelow = cardRect.bottom + UNINSTALL_FLOATING_BIN_OFFSET_PX;
  const overflowsBottom = topBelow + UNINSTALL_FLOATING_BIN_HEIGHT > window.innerHeight - viewportPadding;
  const top = overflowsBottom
    ? Math.max(viewportPadding, cardRect.top - UNINSTALL_FLOATING_BIN_HEIGHT - UNINSTALL_FLOATING_BIN_OFFSET_PX)
    : topBelow;
  return { top, left };
}

/**
 * Returns whether a pointer is over the floating dustbin.
 *
 * @param clientX pointer X
 * @param clientY pointer Y
 * @param top bin top
 * @param left bin left
 * @returns true when the pointer is inside the bin
 */
export function isPointerOverFloatingBin(
  clientX: number,
  clientY: number,
  top: number,
  left: number
): boolean {
  return (
    clientX >= left &&
    clientX <= left + UNINSTALL_FLOATING_BIN_WIDTH &&
    clientY >= top &&
    clientY <= top + UNINSTALL_FLOATING_BIN_HEIGHT
  );
}
