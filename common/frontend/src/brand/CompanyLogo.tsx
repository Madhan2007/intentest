/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Hexagon network company logo used on branded operation screens.
 */
import { memo } from "react";
import {
  COMPANY_LOGO_LABEL,
  LOGO_CANVAS_SIZE,
  LOGO_NODE_COLOR,
  LOGO_NODE_RADIUS,
  LOGO_STROKE_COLOR,
  LOGO_STROKE_WIDTH,
  LOGO_VIEWBOX,
} from "@kernel/brand/brandConstants";
import "@kernel/css/brand.css";

/**
 * Renders the ManageMyOpz network-node logo.
 * @returns The circular logo badge.
 */
function CompanyLogoComponent() {
  return (
    <div className="mmo-logo-badge" aria-label={COMPANY_LOGO_LABEL}>
      <svg
        width={LOGO_CANVAS_SIZE}
        height={LOGO_CANVAS_SIZE}
        viewBox={LOGO_VIEWBOX}
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M 26 16 L 15 21 L 15 26"
          stroke={LOGO_STROKE_COLOR}
          strokeWidth={LOGO_STROKE_WIDTH}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M 34 16 L 45 21 L 45 26"
          stroke={LOGO_STROKE_COLOR}
          strokeWidth={LOGO_STROKE_WIDTH}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M 15 34 L 15 39 L 26 44"
          stroke={LOGO_STROKE_COLOR}
          strokeWidth={LOGO_STROKE_WIDTH}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M 45 34 L 45 39 L 34 44"
          stroke={LOGO_STROKE_COLOR}
          strokeWidth={LOGO_STROKE_WIDTH}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle cx="30" cy="14" r={LOGO_NODE_RADIUS} fill={LOGO_NODE_COLOR} />
        <circle cx="30" cy="46" r={LOGO_NODE_RADIUS} fill={LOGO_NODE_COLOR} />
        <circle cx="15" cy="30" r={LOGO_NODE_RADIUS} fill={LOGO_NODE_COLOR} />
        <circle cx="45" cy="30" r={LOGO_NODE_RADIUS} fill={LOGO_NODE_COLOR} />
        <circle cx="30" cy="30" r={LOGO_NODE_RADIUS} fill={LOGO_NODE_COLOR} />
      </svg>
    </div>
  );
}

export const CompanyLogo = memo(CompanyLogoComponent);
