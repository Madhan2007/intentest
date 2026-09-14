/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Authenticated dashboard landing page.
 */
import { useSessionUser } from "@kernel/auth/SessionStore";
import { ApplicationSection } from "./ApplicationSection";
import { DashboardHeader } from "./DashboardHeader";
import { DashboardWelcome } from "./DashboardWelcome";
import { DASHBOARD_NO_COMPANY_MESSAGE } from "./dashboardConstants";
import { useDashboardCatalog } from "./useDashboardCatalog";
import { useDashboardGestures } from "./useDashboardGestures";
import "@kernel/css/dashboard.css";

/**
 * Renders the signed-in dashboard with installed company applications.
 *
 * @returns The dashboard landing screen
 */
export function DashboardPage() {
  const sessionUser = useSessionUser();
  const {
    searchQuery,
    activeFilterId,
    filterTabs,
    launcherApplications,
    visibleApplications,
    emptyMessage,
    isCatalogLoading,
    catalogError,
    installingApplicationId,
    setSearchQuery,
    setActiveFilterId,
    installApplication,
    uninstallApplication,
    toggleFavourite,
  } = useDashboardCatalog();
  const {
    isApplicationDragActive,
    isDropTargetActive,
    floatingUninstallBin,
    isUninstallBinHot,
    handleApplicationDragStart,
    handleApplicationDragEnd,
    handleShowFloatingUninstallBin,
    setIsDropTargetActive,
  } = useDashboardGestures(uninstallApplication);
  const displayName = sessionUser?.n ?? "";
  const companyId = sessionUser?.c;
  const showCompanyMissingMessage = !companyId && !isCatalogLoading;

  return (
    <div className="mmo-dash-layout">
      <DashboardHeader
        launcherApplications={launcherApplications}
        isCatalogLoading={isCatalogLoading}
        catalogError={catalogError}
        installingApplicationId={installingApplicationId}
        isApplicationDragActive={isApplicationDragActive}
        onInstallApplication={installApplication}
        onUninstallApplication={uninstallApplication}
        onApplicationDragStart={handleApplicationDragStart}
        onApplicationDragEnd={handleApplicationDragEnd}
      />
      <main className="mmo-dash-main">
        <div className="mmo-dash-container">
          <DashboardWelcome displayName={displayName} />
          {showCompanyMissingMessage ? (
            <p className="mmo-dash-empty-text">{DASHBOARD_NO_COMPANY_MESSAGE}</p>
          ) : (
            <ApplicationSection
              applications={visibleApplications}
              filterTabs={filterTabs}
              activeFilterId={activeFilterId}
              emptyMessage={emptyMessage}
              installingApplicationId={installingApplicationId}
              isApplicationDragActive={isApplicationDragActive}
              isDropTargetActive={isDropTargetActive}
              floatingUninstallBin={floatingUninstallBin}
              isUninstallBinHot={isUninstallBinHot}
              searchQuery={searchQuery}
              onSearchQueryChange={setSearchQuery}
              onFilterChange={setActiveFilterId}
              onToggleFavourite={toggleFavourite}
              onInstallApplication={installApplication}
              onUninstallApplication={uninstallApplication}
              onDropTargetChange={setIsDropTargetActive}
              onShowFloatingUninstallBin={handleShowFloatingUninstallBin}
            />
          )}
        </div>
      </main>
    </div>
  );
}
