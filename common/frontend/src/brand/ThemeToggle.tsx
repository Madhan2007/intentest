/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Decorative theme-mode control shared by branded operation screens.
 */
import { memo } from "react";
import {
  ICON_VIEWBOX,
  THEME_MOON_ICON_SIZE,
  THEME_SUN_ICON_SIZE,
  THEME_TOGGLE_MODE_LABEL,
  THEME_TOGGLE_TITLE,
} from "@kernel/brand/brandConstants";
import "@kernel/css/brand.css";

/**
 * Renders the upper-right theme toggle chrome.
 * @returns The theme toggle widget.
 */
function ThemeToggleComponent() {
  return (
    <div className="mmo-theme-control-wrapper">
      <div
        className="mmo-theme-toggle"
        role="button"
        tabIndex={0}
        title={THEME_TOGGLE_TITLE}
      >
        <span className="mmo-toggle-switch">
          <span className="mmo-toggle-knob">
            <svg
              width={THEME_MOON_ICON_SIZE}
              height={THEME_MOON_ICON_SIZE}
              viewBox={ICON_VIEWBOX}
              fill="currentColor"
            >
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
            </svg>
          </span>
          <span className="mmo-toggle-sun">
            <svg
              width={THEME_SUN_ICON_SIZE}
              height={THEME_SUN_ICON_SIZE}
              viewBox={ICON_VIEWBOX}
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="12" cy="12" r="4" fill="currentColor" />
              <line x1="12" y1="2" x2="12" y2="4" />
              <line x1="12" y1="20" x2="12" y2="22" />
              <line x1="4.93" y1="4.93" x2="6.34" y2="6.34" />
              <line x1="17.66" y1="17.66" x2="19.07" y2="19.07" />
              <line x1="2" y1="12" x2="4" y2="12" />
              <line x1="20" y1="12" x2="22" y2="12" />
              <line x1="4.93" y1="19.07" x2="6.34" y2="17.66" />
              <line x1="17.66" y1="6.34" x2="19.07" y2="4.93" />
            </svg>
          </span>
        </span>
        <span className="mmo-toggle-text">{THEME_TOGGLE_MODE_LABEL}</span>
      </div>
    </div>
  );
}

export const ThemeToggle = memo(ThemeToggleComponent);
