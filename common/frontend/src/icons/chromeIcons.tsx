/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Lightweight header icons shared by dashboard and shell chrome.
 */
import { memo } from "react";
import {
  APPS_GRID_DOT_RADIUS,
  ICON_FILL_NONE,
  ICON_SIZE_APPS_GRID,
  ICON_SIZE_BELL,
  ICON_SIZE_MENU,
  ICON_SIZE_SEARCH,
  ICON_STROKE_ROUND,
  ICON_VIEWBOX_18,
  ICON_VIEWBOX_24,
} from "./iconConstants";

interface StrokeIconProps {
  size: number;
}

function SearchIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill={ICON_FILL_NONE}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="8" />
      <path d="m21 21-4.3-4.3" />
    </svg>
  );
}

function BellIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill={ICON_FILL_NONE}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  );
}

function MoreVerticalIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill="currentColor"
      aria-hidden="true"
    >
      <circle cx="12" cy="5" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="12" cy="19" r="1.6" />
    </svg>
  );
}

function MoreHorizontalIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill="currentColor"
      aria-hidden="true"
    >
      <circle cx="5" cy="12" r="1.6" />
      <circle cx="12" cy="12" r="1.6" />
      <circle cx="19" cy="12" r="1.6" />
    </svg>
  );
}

function AppsGridIconComponent() {
  return (
    <svg
      width={ICON_SIZE_APPS_GRID}
      height={ICON_SIZE_APPS_GRID}
      viewBox={ICON_VIEWBOX_18}
      fill="currentColor"
      aria-hidden="true"
    >
      <circle cx="3" cy="3" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="9" cy="3" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="15" cy="3" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="3" cy="9" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="9" cy="9" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="15" cy="9" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="3" cy="15" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="9" cy="15" r={APPS_GRID_DOT_RADIUS} />
      <circle cx="15" cy="15" r={APPS_GRID_DOT_RADIUS} />
    </svg>
  );
}

function StarIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
    </svg>
  );
}

function UninstallIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill={ICON_FILL_NONE}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
    </svg>
  );
}

function InstallIconComponent({ size }: StrokeIconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox={ICON_VIEWBOX_24}
      fill={ICON_FILL_NONE}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap={ICON_STROKE_ROUND}
      strokeLinejoin={ICON_STROKE_ROUND}
      aria-hidden="true"
    >
      <path d="M12 3v12" />
      <path d="m7 11 5 5 5-5" />
      <path d="M5 21h14" />
    </svg>
  );
}

export const SearchIcon = memo(SearchIconComponent);
export const StarIcon = memo(StarIconComponent);
export const InstallIcon = memo(InstallIconComponent);
export const UninstallIcon = memo(UninstallIconComponent);
export const BellIcon = memo(BellIconComponent);
export const MoreVerticalIcon = memo(MoreVerticalIconComponent);
export const MoreHorizontalIcon = memo(MoreHorizontalIconComponent);
export const AppsGridIcon = memo(AppsGridIconComponent);

export const SEARCH_ICON_SIZE = ICON_SIZE_SEARCH;
export const BELL_ICON_SIZE = ICON_SIZE_BELL;
export const MENU_ICON_SIZE = ICON_SIZE_MENU;
