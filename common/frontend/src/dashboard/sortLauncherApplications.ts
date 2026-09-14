/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Launcher order: installed, licensed, then unlicensed.
 */
import type { ApplicationItem } from "./applicationCatalog";
import { LICENSE_STATE_INSTALLED, LICENSE_STATE_LICENSED } from "./dashboardConstants";

function licenseRank(licenseState: ApplicationItem["licenseState"]): number {
  if (licenseState === LICENSE_STATE_INSTALLED) {
    return 0;
  }
  if (licenseState === LICENSE_STATE_LICENSED) {
    return 1;
  }
  return 2;
}

/**
 * Orders launcher applications installed first, then licensed, then unlicensed.
 *
 * @param applications company catalog
 * @returns sorted launcher applications
 */
export function sortLauncherApplications(
  applications: readonly ApplicationItem[]
): ApplicationItem[] {
  return [...applications].sort((left, right) => {
    const rankDifference = licenseRank(left.licenseState) - licenseRank(right.licenseState);
    if (rankDifference !== 0) {
      return rankDifference;
    }
    return left.sortOrder - right.sortOrder;
  });
}
