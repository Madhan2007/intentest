/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Reusable application catalog card with favourite and uninstall.
 */
import { memo, useCallback, useRef, type MouseEvent, type PointerEvent } from "react";
import { useLongPress } from "@kernel/hooks/useLongPress";
import { MENU_ICON_SIZE, StarIcon } from "@kernel/icons/chromeIcons";
import type { ApplicationItem } from "./applicationCatalog";
import { ApplicationCardMenu } from "./ApplicationCardMenu";
import { ApplicationGlyph } from "./ApplicationGlyph";
import {
  DASHBOARD_CARD_FAVOURITE_LABEL_PREFIX,
  DASHBOARD_CARD_UNFAVOURITE_LABEL_PREFIX,
} from "./dashboardConstants";
import { InstallProgressBar } from "./InstallProgressBar";

interface ApplicationCardProps {
  application: ApplicationItem;
  isInstalling: boolean;
  isHoldUninstallActive: boolean;
  onToggleFavourite: (applicationId: string, favourite: boolean) => Promise<void>;
  onUninstall: (applicationId: string) => Promise<void>;
  onShowFloatingUninstallBin: (applicationId: string, cardRect: DOMRect) => void;
}

/**
 * Renders one installed or installing application.
 *
 * @param props catalog item and hold-to-uninstall bindings
 * @returns Application card
 */
function ApplicationCardComponent({
  application,
  isInstalling,
  isHoldUninstallActive,
  onToggleFavourite,
  onUninstall,
  onShowFloatingUninstallBin,
}: ApplicationCardProps) {
  const cardRef = useRef<HTMLElement>(null);
  const favouriteLabel = application.favourite
    ? `${DASHBOARD_CARD_UNFAVOURITE_LABEL_PREFIX} ${application.name}`
    : `${DASHBOARD_CARD_FAVOURITE_LABEL_PREFIX} ${application.name}`;

  const handleLongPress = useCallback(() => {
    const cardElement = cardRef.current;
    if (!cardElement) {
      return;
    }
    onShowFloatingUninstallBin(application.id, cardElement.getBoundingClientRect());
  }, [application.id, onShowFloatingUninstallBin]);

  const { onPointerDown, onPointerUp, onPointerCancel } = useLongPress(handleLongPress, isInstalling);

  const handleFavouriteClick = useCallback(() => {
    void onToggleFavourite(application.id, !application.favourite);
  }, [application.favourite, application.id, onToggleFavourite]);

  const handleUninstallClick = useCallback(() => {
    void onUninstall(application.id);
  }, [application.id, onUninstall]);

  const handlePointerDown = useCallback(
    (pointerEvent: PointerEvent<HTMLElement>) => {
      const eventTarget = pointerEvent.target;
      if (eventTarget instanceof Element && eventTarget.closest("button")) {
        return;
      }
      onPointerDown(pointerEvent);
    },
    [onPointerDown]
  );

  const handleContextMenu = useCallback((mouseEvent: MouseEvent<HTMLElement>) => {
    mouseEvent.preventDefault();
  }, []);

  return (
    <article
      ref={cardRef}
      className={cardClassName(isInstalling, isHoldUninstallActive)}
      onPointerDown={handlePointerDown}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerCancel}
      onContextMenu={handleContextMenu}
    >
      <div className="mmo-dash-card-top">
        <div
          className="mmo-dash-card-icon"
          style={{ backgroundColor: application.iconBackgroundColor }}
          aria-hidden="true"
        >
          <ApplicationGlyph iconId={application.iconId} />
        </div>
        {isInstalling ? null : (
          <div className="mmo-dash-card-actions">
            <button
              type="button"
              className={
                application.favourite
                  ? "mmo-dash-card-favourite mmo-dash-card-favourite-active"
                  : "mmo-dash-card-favourite"
              }
              aria-label={favouriteLabel}
              title={favouriteLabel}
              aria-pressed={application.favourite}
              onClick={handleFavouriteClick}
            >
              <StarIcon size={MENU_ICON_SIZE} />
            </button>
            <ApplicationCardMenu
              applicationName={application.name}
              onUninstall={handleUninstallClick}
            />
          </div>
        )}
      </div>
      <div className="mmo-dash-card-body">
        <h3 className="mmo-dash-card-title">{application.name}</h3>
        <p className="mmo-dash-card-description">{application.description}</p>
      </div>
      {isInstalling ? (
        <InstallProgressBar
          trackClassName="mmo-dash-card-progress"
          barClassName="mmo-dash-card-progress-bar"
        />
      ) : null}
    </article>
  );
}

function cardClassName(isInstalling: boolean, isHoldUninstallActive: boolean): string {
  if (isInstalling) {
    return "mmo-dash-card mmo-dash-card-installing";
  }
  if (isHoldUninstallActive) {
    return "mmo-dash-card mmo-dash-card-uninstall-ready";
  }
  return "mmo-dash-card";
}

export const ApplicationCard = memo(ApplicationCardComponent);
