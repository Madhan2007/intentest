/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Reusable application catalog grid.
 */
import { memo } from "react";
import type { ApplicationItem } from "./applicationCatalog";
import { ApplicationCard } from "./ApplicationCard";

interface ApplicationGridProps {
  applications: readonly ApplicationItem[];
  emptyMessage: string;
  installingApplicationId: string | null;
  holdUninstallApplicationId: string | null;
  onToggleFavourite: (applicationId: string, favourite: boolean) => Promise<void>;
  onUninstall: (applicationId: string) => Promise<void>;
  onShowFloatingUninstallBin: (applicationId: string, cardRect: DOMRect) => void;
}

/**
 * Renders catalog cards or an empty state.
 *
 * @param props filtered applications and uninstall bindings
 * @returns Application grid
 */
function ApplicationGridComponent({
  applications,
  emptyMessage,
  installingApplicationId,
  holdUninstallApplicationId,
  onToggleFavourite,
  onUninstall,
  onShowFloatingUninstallBin,
}: ApplicationGridProps) {
  if (applications.length === 0) {
    return (
      <div className="mmo-dash-empty">
        <p className="mmo-dash-empty-text">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="mmo-dash-grid">
      {applications.map((application) => (
        <ApplicationCard
          key={application.id}
          application={application}
          isInstalling={application.id === installingApplicationId}
          isHoldUninstallActive={application.id === holdUninstallApplicationId}
          onToggleFavourite={onToggleFavourite}
          onUninstall={onUninstall}
          onShowFloatingUninstallBin={onShowFloatingUninstallBin}
        />
      ))}
    </div>
  );
}

export const ApplicationGrid = memo(ApplicationGridComponent);
