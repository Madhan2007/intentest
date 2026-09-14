/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Installed applications heading, filters, and drop zones.
 */
import { memo, useCallback, type DragEvent } from "react";
import type { ApplicationFilterId, ApplicationFilterTab, ApplicationItem } from "./applicationCatalog";
import { ApplicationFilters } from "./ApplicationFilters";
import { ApplicationGrid } from "./ApplicationGrid";
import { isApplicationInstallDrag, readDraggedApplicationId } from "./applicationDrag";
import {
  DASHBOARD_APPLICATIONS_TITLE,
  DASHBOARD_INSTALL_DROP_LABEL,
  DRAG_EFFECT_COPY,
} from "./dashboardConstants";
import { isDragStillInsideTarget } from "./dragTarget";
import type { FloatingUninstallBinPosition } from "./floatingUninstallBin";
import { UninstallDropBin } from "./UninstallDropBin";

interface ApplicationSectionProps {
  applications: readonly ApplicationItem[];
  filterTabs: readonly ApplicationFilterTab[];
  activeFilterId: ApplicationFilterId;
  emptyMessage: string;
  installingApplicationId: string | null;
  isApplicationDragActive: boolean;
  isDropTargetActive: boolean;
  floatingUninstallBin: FloatingUninstallBinPosition | null;
  isUninstallBinHot: boolean;
  searchQuery: string;
  onSearchQueryChange: (searchQuery: string) => void;
  onFilterChange: (filterId: ApplicationFilterId) => void;
  onToggleFavourite: (applicationId: string, favourite: boolean) => Promise<void>;
  onInstallApplication: (applicationId: string) => Promise<void>;
  onUninstallApplication: (applicationId: string) => Promise<void>;
  onDropTargetChange: (isActive: boolean) => void;
  onShowFloatingUninstallBin: (applicationId: string, cardRect: DOMRect) => void;
}

/**
 * Renders the installed applications block and accepts install drops.
 *
 * @param props installed applications and gesture bindings
 * @returns Applications section
 */
function ApplicationSectionComponent({
  applications,
  filterTabs,
  activeFilterId,
  emptyMessage,
  installingApplicationId,
  isApplicationDragActive,
  isDropTargetActive,
  floatingUninstallBin,
  isUninstallBinHot,
  searchQuery,
  onSearchQueryChange,
  onFilterChange,
  onToggleFavourite,
  onInstallApplication,
  onUninstallApplication,
  onDropTargetChange,
  onShowFloatingUninstallBin,
}: ApplicationSectionProps) {
  const handleDragOver = useCallback(
    (dragEvent: DragEvent<HTMLElement>) => {
      if (!isApplicationInstallDrag(dragEvent.dataTransfer, isApplicationDragActive)) {
        return;
      }
      dragEvent.preventDefault();
      dragEvent.dataTransfer.dropEffect = DRAG_EFFECT_COPY;
      onDropTargetChange(true);
    },
    [isApplicationDragActive, onDropTargetChange]
  );

  const handleDragLeave = useCallback(
    (dragEvent: DragEvent<HTMLElement>) => {
      if (isDragStillInsideTarget(dragEvent)) {
        return;
      }
      onDropTargetChange(false);
    },
    [onDropTargetChange]
  );

  const handleDrop = useCallback(
    (dragEvent: DragEvent<HTMLElement>) => {
      dragEvent.preventDefault();
      onDropTargetChange(false);
      const applicationId = readDraggedApplicationId(dragEvent.dataTransfer);
      if (applicationId) {
        void onInstallApplication(applicationId);
      }
    },
    [onDropTargetChange, onInstallApplication]
  );

  return (
    <section
      className={isDropTargetActive ? "mmo-dash-apps mmo-dash-apps-drop-active" : "mmo-dash-apps"}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <div className="mmo-dash-apps-header">
        <h2 className="mmo-dash-apps-title">{DASHBOARD_APPLICATIONS_TITLE}</h2>
        <ApplicationFilters
          filterTabs={filterTabs}
          activeFilterId={activeFilterId}
          searchQuery={searchQuery}
          onSearchQueryChange={onSearchQueryChange}
          onFilterChange={onFilterChange}
        />
      </div>
      {isDropTargetActive ? (
        <p className="mmo-dash-apps-drop-hint">{DASHBOARD_INSTALL_DROP_LABEL}</p>
      ) : null}
      <ApplicationGrid
        applications={applications}
        emptyMessage={emptyMessage}
        installingApplicationId={installingApplicationId}
        holdUninstallApplicationId={floatingUninstallBin?.applicationId ?? null}
        onToggleFavourite={onToggleFavourite}
        onUninstall={onUninstallApplication}
        onShowFloatingUninstallBin={onShowFloatingUninstallBin}
      />
      {floatingUninstallBin ? (
        <UninstallDropBin position={floatingUninstallBin} isUninstallBinHot={isUninstallBinHot} />
      ) : null}
    </section>
  );
}

export const ApplicationSection = memo(ApplicationSectionComponent);
