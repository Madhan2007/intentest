/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Dashboard application catalog types and filter helpers.
 */
import {
  FILTER_ALL,
  FILTER_CATEGORY_PREFIX,
  FILTER_FAVOURITE,
  FILTER_TAB_ALL_LABEL,
  FILTER_TAB_FAVOURITE_LABEL,
  ICON_ID_DATA,
  ICON_ID_DESK,
  ICON_ID_FINANCE,
  ICON_ID_HR,
  ICON_ID_INVENTORY,
  ICON_ID_MARKETING,
  ICON_ID_PROJECT,
  ICON_ID_SALES,
  ICON_ID_SHOP,
  ICON_ID_VAULT,
  LICENSE_STATE_INSTALLED,
  LICENSE_STATE_LICENSED,
  LICENSE_STATE_UNLICENSED,
} from "./dashboardConstants";

export type ApplicationLicenseState =
  | typeof LICENSE_STATE_UNLICENSED
  | typeof LICENSE_STATE_LICENSED
  | typeof LICENSE_STATE_INSTALLED;

export type ApplicationIconId =
  | typeof ICON_ID_DATA
  | typeof ICON_ID_DESK
  | typeof ICON_ID_HR
  | typeof ICON_ID_MARKETING
  | typeof ICON_ID_FINANCE
  | typeof ICON_ID_SALES
  | typeof ICON_ID_INVENTORY
  | typeof ICON_ID_SHOP
  | typeof ICON_ID_PROJECT
  | typeof ICON_ID_VAULT;

export type ApplicationFilterId = string;

export interface ApplicationItem {
  id: string;
  companyApplicationId: string;
  appKey: string;
  productCode: string;
  category: string;
  name: string;
  description: string;
  iconId: ApplicationIconId;
  iconBackgroundColor: string;
  sortOrder: number;
  licenseState: ApplicationLicenseState;
  favourite: boolean;
}

export interface ApplicationFilterTab {
  id: ApplicationFilterId;
  label: string;
}

const APPLICATION_ICON_IDS: readonly ApplicationIconId[] = [
  ICON_ID_DATA,
  ICON_ID_DESK,
  ICON_ID_HR,
  ICON_ID_MARKETING,
  ICON_ID_FINANCE,
  ICON_ID_SALES,
  ICON_ID_INVENTORY,
  ICON_ID_SHOP,
  ICON_ID_PROJECT,
  ICON_ID_VAULT,
];

/**
 * Maps a catalog icon key onto the known glyph set.
 *
 * @param iconKey stored icon_key
 * @returns a supported glyph id
 */
export function applicationIconIdFromKey(iconKey: string): ApplicationIconId {
  return APPLICATION_ICON_IDS.includes(iconKey as ApplicationIconId)
    ? (iconKey as ApplicationIconId)
    : ICON_ID_DATA;
}

/**
 * Confirms a license state value from the applications API.
 *
 * @param licenseState stored license_state
 * @returns a known license state
 */
export function applicationLicenseStateFromValue(licenseState: string): ApplicationLicenseState {
  if (
    licenseState === LICENSE_STATE_UNLICENSED ||
    licenseState === LICENSE_STATE_LICENSED ||
    licenseState === LICENSE_STATE_INSTALLED
  ) {
    return licenseState;
  }
  return LICENSE_STATE_UNLICENSED;
}

/**
 * Builds All, Favourite, and one tab per catalog category.
 *
 * @param applications company catalog
 * @returns dashboard filter tabs
 */
export function buildDashboardFilterTabs(
  applications: readonly ApplicationItem[]
): ApplicationFilterTab[] {
  const categories = new Map<string, string>();
  for (const application of applications) {
    if (!application.category) {
      continue;
    }
    if (!categories.has(application.category)) {
      categories.set(application.category, categoryLabel(application.category));
    }
  }
  return [
    { id: FILTER_ALL, label: FILTER_TAB_ALL_LABEL },
    { id: FILTER_FAVOURITE, label: FILTER_TAB_FAVOURITE_LABEL },
    ...[...categories.entries()]
      .sort((left, right) => left[1].localeCompare(right[1]))
      .map(([category, label]) => ({
        id: `${FILTER_CATEGORY_PREFIX}${category}`,
        label,
      })),
  ];
}

/**
 * Formats a stored category key for the filter tab.
 *
 * @param category stored category
 * @returns display label
 */
export function categoryLabel(category: string): string {
  if (category === ICON_ID_HR) {
    return "HR";
  }
  if (!category) {
    return "";
  }
  return category.charAt(0).toUpperCase() + category.slice(1);
}
