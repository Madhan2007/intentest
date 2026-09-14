/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Icon-only application search that expands beside the filter tabs.
 */
import { memo, useCallback, useRef, useState, type ChangeEvent } from "react";
import { useDismissOnOutsideEvent } from "@kernel/hooks/useDismissOnOutsideEvent";
import { INPUT_TYPE_TEXT } from "@kernel/fields/formConstants";
import { SearchIcon, SEARCH_ICON_SIZE } from "@kernel/icons/chromeIcons";
import { DASHBOARD_SEARCH_LABEL, DASHBOARD_SEARCH_PLACEHOLDER } from "./dashboardConstants";

interface ApplicationSearchProps {
  searchQuery: string;
  onSearchQueryChange: (searchQuery: string) => void;
}

/**
 * Renders a search icon that expands into a field before the All filter.
 *
 * @param props search value and change handler
 * @returns compact application search control
 */
function ApplicationSearchComponent({ searchQuery, onSearchQueryChange }: ApplicationSearchProps) {
  const [isExpanded, setIsExpanded] = useState(searchQuery.length > 0);
  const searchRootRef = useRef<HTMLDivElement>(null);
  const hasActiveQuery = searchQuery.trim().length > 0;

  const collapseSearch = useCallback(() => {
    setIsExpanded(false);
  }, []);

  useDismissOnOutsideEvent(isExpanded, searchRootRef, collapseSearch);

  const handleTriggerClick = useCallback(() => {
    setIsExpanded(true);
  }, []);

  const handleSearchChange = useCallback(
    (changeEvent: ChangeEvent<HTMLInputElement>) => {
      onSearchQueryChange(changeEvent.target.value);
    },
    [onSearchQueryChange]
  );

  return (
    <div
      className={isExpanded ? "mmo-dash-filter-search mmo-dash-filter-search-open" : "mmo-dash-filter-search"}
      ref={searchRootRef}
    >
      <button
        type="button"
        className={
          hasActiveQuery
            ? "mmo-dash-filter-search-trigger mmo-dash-filter-search-trigger-active"
            : "mmo-dash-filter-search-trigger"
        }
        aria-label={DASHBOARD_SEARCH_LABEL}
        title={DASHBOARD_SEARCH_LABEL}
        aria-expanded={isExpanded}
        onClick={handleTriggerClick}
      >
        <SearchIcon size={SEARCH_ICON_SIZE} />
      </button>
      {isExpanded ? (
        <div className="mmo-dash-filter-search-field">
          <input
            type={INPUT_TYPE_TEXT}
            autoFocus
            className="mmo-dash-filter-search-input"
            placeholder={DASHBOARD_SEARCH_PLACEHOLDER}
            aria-label={DASHBOARD_SEARCH_LABEL}
            value={searchQuery}
            onChange={handleSearchChange}
          />
          <span className="mmo-dash-filter-search-icon" aria-hidden="true">
            <SearchIcon size={SEARCH_ICON_SIZE} />
          </span>
        </div>
      ) : null}
    </div>
  );
}

export const ApplicationSearch = memo(ApplicationSearchComponent);
