/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Shared CSS-driven install progress bar for tiles and cards.
 */
import { memo } from "react";
import {
  DASHBOARD_LAUNCHER_INSTALLING_LABEL,
  INSTALL_PROGRESS_COMPLETE,
  INSTALL_PROGRESS_FILL_CLASS_NAME,
  INSTALL_PROGRESS_MAX_BEFORE_COMPLETE,
  INSTALL_PROGRESS_MIN_MS,
  INSTALL_PROGRESS_MIN_VALUE,
} from "./dashboardConstants";

interface InstallProgressBarProps {
  trackClassName: string;
  barClassName: string;
}

/**
 * Renders an install progress bar animated by CSS, not React state ticks.
 *
 * @param props style class names
 * @returns progress bar
 */
function InstallProgressBarComponent({ trackClassName, barClassName }: InstallProgressBarProps) {
  return (
    <div
      className={trackClassName}
      role="progressbar"
      aria-valuemin={INSTALL_PROGRESS_MIN_VALUE}
      aria-valuemax={INSTALL_PROGRESS_COMPLETE}
      aria-valuenow={INSTALL_PROGRESS_MAX_BEFORE_COMPLETE}
      aria-label={DASHBOARD_LAUNCHER_INSTALLING_LABEL}
    >
      <span
        className={`${barClassName} ${INSTALL_PROGRESS_FILL_CLASS_NAME}`}
        style={{ animationDuration: `${INSTALL_PROGRESS_MIN_MS}ms` }}
      />
    </div>
  );
}

export const InstallProgressBar = memo(InstallProgressBarComponent);
