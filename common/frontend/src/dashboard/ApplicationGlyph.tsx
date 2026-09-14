/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Application glyph set for dashboard cards and other pickers.
 */
import { memo, type ReactNode } from "react";
import {
  ICON_FILL_NONE,
  ICON_SIZE_APPLICATION,
  ICON_STROKE_ROUND,
  ICON_STROKE_WIDTH_APPLICATION,
  ICON_VIEWBOX_24,
} from "@kernel/icons/iconConstants";
import {
  ICON_FOREGROUND_WHITE,
  ICON_ID_DATA,
  ICON_ID_DESK,
  ICON_ID_FINANCE,
  ICON_ID_HR,
  ICON_ID_INVENTORY,
  ICON_ID_MARKETING,
  ICON_ID_PROJECT,
  ICON_ID_SALES,
  ICON_ID_SHOP,
  ICON_ID_VAULT,
} from "./dashboardConstants";
import type { ApplicationIconId } from "./applicationCatalog";

interface ApplicationGlyphProps {
  iconId: ApplicationIconId;
}

const PEOPLE_GLYPH = (
  <>
    <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
    <circle cx="9" cy="7" r="4" />
    <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
    <path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </>
);

const APPLICATION_GLYPH_PATHS: Record<ApplicationIconId, ReactNode> = {
  [ICON_ID_DATA]: (
    <>
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
      <path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3" />
    </>
  ),
  [ICON_ID_DESK]: (
    <>
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <path d="M8 21h8" />
      <path d="M12 17v4" />
    </>
  ),
  [ICON_ID_HR]: PEOPLE_GLYPH,
  [ICON_ID_SALES]: PEOPLE_GLYPH,
  [ICON_ID_MARKETING]: (
    <>
      <path d="M3 11v2a1 1 0 0 0 1 1h2l3 4V6L6 10H4a1 1 0 0 0-1 1z" />
      <path d="M15.5 8.5a5 5 0 0 1 0 7" />
      <path d="M18.5 6a9 9 0 0 1 0 12" />
    </>
  ),
  [ICON_ID_FINANCE]: (
    <>
      <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h12a1 1 0 0 1 1 1v3" />
      <path d="M3 5v14a2 2 0 0 0 2 2h12a1 1 0 0 0 1-1v-3" />
      <circle cx="16.5" cy="16.5" r="2.5" />
    </>
  ),
  [ICON_ID_INVENTORY]: (
    <>
      <path d="M16.5 9.4 7.55 4.24" />
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <path d="M3.29 7 12 12l8.71-5" />
      <path d="M12 22V12" />
    </>
  ),
  [ICON_ID_SHOP]: (
    <>
      <circle cx="8" cy="21" r="1" />
      <circle cx="19" cy="21" r="1" />
      <path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57L23 6H6" />
    </>
  ),
  [ICON_ID_PROJECT]: (
    <>
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <path d="m9 11 3 3L22 4" />
    </>
  ),
  [ICON_ID_VAULT]: (
    <>
      <rect x="3" y="11" width="18" height="11" rx="2" />
      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
    </>
  ),
};

function ApplicationGlyphComponent({ iconId }: ApplicationGlyphProps) {
  return (
    <svg
      width={ICON_SIZE_APPLICATION}
      height={ICON_SIZE_APPLICATION}
      viewBox={ICON_VIEWBOX_24}
      fill={ICON_FILL_NONE}
      stroke={ICON_FOREGROUND_WHITE}
      strokeWidth={ICON_STROKE_WIDTH_APPLICATION}
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      {APPLICATION_GLYPH_PATHS[iconId]}
    </svg>
  );
}

export const ApplicationGlyph = memo(ApplicationGlyphComponent);
