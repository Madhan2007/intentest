/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Dashboard filter tabs for All, Favourite, and catalog categories.
 */
import { memo, useCallback, type MouseEvent } from "react";
import type { ApplicationFilterId, ApplicationFilterTab } from "./applicationCatalog";
import { ApplicationSearch } from "./ApplicationSearch";
import { DASHBOARD_FILTERS_LABEL } from "./dashboardConstants";

const FILTER_ID_ATTRIBUTE = "data-filter-id";
const FILTER_CLASS_NAME = "mmo-dash-filter";
const FILTER_ACTIVE_CLASS_NAME = "mmo-dash-filter mmo-dash-filter-active";

interface ApplicationFiltersProps {
  filterTabs: readonly ApplicationFilterTab[];
  activeFilterId: ApplicationFilterId;
  searchQuery: string;
  onSearchQueryChange: (searchQuery: string) => void;
  onFilterChange: (filterId: ApplicationFilterId) => void;
}

/**
 * Renders search plus All, Favourite, and category tabs.
 *
 * @param props search, filter tabs, and the active selection
 * @returns Filter toolbar
 */
function ApplicationFiltersComponent({
  filterTabs,
  activeFilterId,
  searchQuery,
  onSearchQueryChange,
  onFilterChange,
}: ApplicationFiltersProps) {
  const handleFilterClick = useCallback(
    (clickEvent: MouseEvent<HTMLButtonElement>) => {
      const selectedFilterId = clickEvent.currentTarget.getAttribute(FILTER_ID_ATTRIBUTE);
      if (selectedFilterId) {
        onFilterChange(selectedFilterId);
      }
    },
    [onFilterChange]
  );

  return (
    <div className="mmo-dash-filters">
      <ApplicationSearch searchQuery={searchQuery} onSearchQueryChange={onSearchQueryChange} />
      <div role="tablist" aria-label={DASHBOARD_FILTERS_LABEL} className="mmo-dash-filter-tabs">
        {filterTabs.map((filterTab) => {
          const isActiveFilter = activeFilterId === filterTab.id;
          return (
            <button
              key={filterTab.id}
              type="button"
              role="tab"
              aria-selected={isActiveFilter}
              className={isActiveFilter ? FILTER_ACTIVE_CLASS_NAME : FILTER_CLASS_NAME}
              data-filter-id={filterTab.id}
              onClick={handleFilterClick}
            >
              {filterTab.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export const ApplicationFilters = memo(ApplicationFiltersComponent);
