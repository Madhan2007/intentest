/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Drag payload helpers for install and uninstall gestures.
 */
import {
  DASHBOARD_DRAG_APPLICATION_PREFIX,
  DASHBOARD_DRAG_APPLICATION_TYPE,
  DASHBOARD_DRAG_UNINSTALL_PREFIX,
  DASHBOARD_DRAG_UNINSTALL_TYPE,
  DRAG_EFFECT_COPY,
  DRAG_EFFECT_MOVE,
  TEXT_PLAIN_MIME_TYPE,
} from "./dashboardConstants";

/**
 * Writes a licensed application id onto a drag event.
 *
 * @param dataTransfer browser drag payload
 * @param applicationId catalog application id
 */
export function writeDraggedApplicationId(
  dataTransfer: DataTransfer,
  applicationId: string
): void {
  dataTransfer.effectAllowed = DRAG_EFFECT_COPY;
  dataTransfer.setData(DASHBOARD_DRAG_APPLICATION_TYPE, applicationId);
  dataTransfer.setData(TEXT_PLAIN_MIME_TYPE, `${DASHBOARD_DRAG_APPLICATION_PREFIX}${applicationId}`);
}

/**
 * Writes an installed application id onto an uninstall drag event.
 *
 * @param dataTransfer browser drag payload
 * @param applicationId catalog application id
 */
export function writeUninstallApplicationId(
  dataTransfer: DataTransfer,
  applicationId: string
): void {
  dataTransfer.effectAllowed = DRAG_EFFECT_MOVE;
  dataTransfer.setData(DASHBOARD_DRAG_UNINSTALL_TYPE, applicationId);
  dataTransfer.setData(TEXT_PLAIN_MIME_TYPE, `${DASHBOARD_DRAG_UNINSTALL_PREFIX}${applicationId}`);
}

/**
 * Reads a dragged application id from an install drop event.
 *
 * @param dataTransfer browser drag payload
 * @returns catalog application id, or null
 */
export function readDraggedApplicationId(dataTransfer: DataTransfer): string | null {
  const typedId = dataTransfer.getData(DASHBOARD_DRAG_APPLICATION_TYPE);
  if (typedId) {
    return typedId;
  }
  const payload = dataTransfer.getData(TEXT_PLAIN_MIME_TYPE);
  if (!payload.startsWith(DASHBOARD_DRAG_APPLICATION_PREFIX)) {
    return null;
  }
  const applicationId = payload.slice(DASHBOARD_DRAG_APPLICATION_PREFIX.length);
  return applicationId || null;
}

/**
 * Reads a dragged application id from an uninstall drop event.
 *
 * @param dataTransfer browser drag payload
 * @returns catalog application id, or null
 */
export function readUninstallApplicationId(dataTransfer: DataTransfer): string | null {
  const typedId = dataTransfer.getData(DASHBOARD_DRAG_UNINSTALL_TYPE);
  if (typedId) {
    return typedId;
  }
  const payload = dataTransfer.getData(TEXT_PLAIN_MIME_TYPE);
  if (!payload.startsWith(DASHBOARD_DRAG_UNINSTALL_PREFIX)) {
    return null;
  }
  const applicationId = payload.slice(DASHBOARD_DRAG_UNINSTALL_PREFIX.length);
  return applicationId || null;
}

/**
 * Returns whether the current drag is a licensed application install.
 *
 * @param dataTransfer browser drag payload
 * @param isApplicationDragActive true when the launcher started an app drag
 * @returns true when the dashboard should accept the drop
 */
export function isApplicationInstallDrag(
  dataTransfer: DataTransfer,
  isApplicationDragActive: boolean
): boolean {
  if (dataTransfer.types.includes(DASHBOARD_DRAG_UNINSTALL_TYPE)) {
    return false;
  }
  if (isApplicationDragActive) {
    return true;
  }
  return dataTransfer.types.includes(DASHBOARD_DRAG_APPLICATION_TYPE);
}

/**
 * Returns whether the current drag is an uninstall gesture.
 *
 * @param dataTransfer browser drag payload
 * @param isUninstallDragActive true when a dashboard card started an uninstall drag
 * @returns true when the dustbin should accept the drop
 */
export function isApplicationUninstallDrag(
  dataTransfer: DataTransfer,
  isUninstallDragActive: boolean
): boolean {
  if (isUninstallDragActive) {
    return true;
  }
  return dataTransfer.types.includes(DASHBOARD_DRAG_UNINSTALL_TYPE);
}
