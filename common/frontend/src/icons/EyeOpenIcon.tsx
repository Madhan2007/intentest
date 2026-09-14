/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Open-eye icon for revealing a password field.
 */
import { memo } from "react";
import {
  PASSWORD_ICON_SIZE,
  PASSWORD_ICON_STROKE_WIDTH,
  PASSWORD_ICON_VIEWBOX,
} from "@kernel/fields/formConstants";

/**
 * Renders the visible-password eye icon.
 * @returns SVG eye icon.
 */
function EyeOpenIconComponent() {
  return (
    <svg
      width={PASSWORD_ICON_SIZE}
      height={PASSWORD_ICON_SIZE}
      viewBox={PASSWORD_ICON_VIEWBOX}
      fill="none"
      stroke="currentColor"
      strokeWidth={PASSWORD_ICON_STROKE_WIDTH}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export const EyeOpenIcon = memo(EyeOpenIconComponent);
