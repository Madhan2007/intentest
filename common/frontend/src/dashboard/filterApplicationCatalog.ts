/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Search and filter helper for installed dashboard applications.
 */
import type { ApplicationFilterId, ApplicationItem } from "./applicationCatalog";
import {
  FILTER_ALL,
  FILTER_CATEGORY_PREFIX,
  FILTER_FAVOURITE,
  LICENSE_STATE_INSTALLED,
} from "./dashboardConstants";

/**
 * Returns installed applications matching search and the active dashboard filter.
 *
 * @param catalog company applications
 * @param searchQuery search text
 * @param filterId All, Favourite, or a category tab
 * @param installingApplicationId app currently installing, shown on every tab
 * @returns matching installed applications
 */
export function filterInstalledApplications(
  catalog: readonly ApplicationItem[],
  searchQuery: string,
  filterId: ApplicationFilterId = FILTER_ALL,
  installingApplicationId: string | null = null
): ApplicationItem[] {
  const normalizedSearchQuery = searchQuery.trim().toLowerCase();
  return catalog.filter((application) => {
    const isInstalling = application.id === installingApplicationId;
    if (isInstalling) {
      return true;
    }
    if (application.licenseState !== LICENSE_STATE_INSTALLED) {
      return false;
    }
    if (!matchesFilter(application, filterId)) {
      return false;
    }
    if (!normalizedSearchQuery) {
      return true;
    }
    return (
      application.name.toLowerCase().includes(normalizedSearchQuery) ||
      application.description.toLowerCase().includes(normalizedSearchQuery) ||
      application.productCode.toLowerCase().includes(normalizedSearchQuery) ||
      application.category.toLowerCase().includes(normalizedSearchQuery)
    );
  });
}

function matchesFilter(application: ApplicationItem, filterId: ApplicationFilterId): boolean {
  if (filterId === FILTER_ALL) {
    return true;
  }
  if (filterId === FILTER_FAVOURITE) {
    return application.favourite;
  }
  if (filterId.startsWith(FILTER_CATEGORY_PREFIX)) {
    return application.category === filterId.slice(FILTER_CATEGORY_PREFIX.length);
  }
  return true;
}
