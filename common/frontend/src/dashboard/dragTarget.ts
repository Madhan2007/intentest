/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Shared drag-leave helper for nested drop targets.
 */
import type { DragEvent } from "react";

/**
 * Returns whether the pointer is still inside the current drop target.
 *
 * @param dragEvent browser drag leave event
 * @returns true when a child still owns the pointer
 */
export function isDragStillInsideTarget(dragEvent: DragEvent<HTMLElement>): boolean {
  const nextTarget = dragEvent.relatedTarget;
  return nextTarget instanceof Node && dragEvent.currentTarget.contains(nextTarget);
}
