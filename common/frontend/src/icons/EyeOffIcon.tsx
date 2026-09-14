/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Slashed-eye icon for hiding a password field.
 */
import { memo } from "react";
import {
  PASSWORD_ICON_SIZE,
  PASSWORD_ICON_STROKE_WIDTH,
  PASSWORD_ICON_VIEWBOX,
} from "@kernel/fields/formConstants";

/**
 * Renders the hidden-password slashed-eye icon.
 * @returns SVG slashed-eye icon.
 */
function EyeOffIconComponent() {
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
      <path
        d={
          "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8" +
          "a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4" +
          "c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"
        }
      />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  );
}

export const EyeOffIcon = memo(EyeOffIconComponent);
