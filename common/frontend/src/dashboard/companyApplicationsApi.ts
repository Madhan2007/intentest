/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Company application catalog HTTP client.
 */
import { httpOpzhub } from "@kernel/api/http-client";
import {
  applicationIconIdFromKey,
  applicationLicenseStateFromValue,
  type ApplicationItem,
} from "./applicationCatalog";

const APPS_PATH = "/apps";
const APPS_LICENSE_PATH = "/apps/license";
const APPS_INSTALL_PATH = "/apps/install";
const APPS_UNINSTALL_PATH = "/apps/uninstall";
const APPS_FAVOURITE_PATH = "/apps/favourite";

interface CompanyApplicationPayload {
  id: string;
  companyApplicationId: string;
  appKey: string;
  productCode: string;
  category: string;
  name: string;
  description: string;
  iconKey: string;
  iconBackgroundColor: string;
  sortOrder: number;
  licenseState: string;
  favourite: boolean;
}

interface CompanyApplicationsPayload {
  applications: CompanyApplicationPayload[];
}

/**
 * Maps one API application row onto the dashboard catalog item.
 *
 * @param payload API application row
 * @returns catalog item
 */
export function mapCompanyApplication(payload: CompanyApplicationPayload): ApplicationItem {
  return {
    id: payload.id,
    companyApplicationId: payload.companyApplicationId,
    appKey: payload.appKey,
    productCode: payload.productCode ?? "",
    category: payload.category ?? "",
    name: payload.name,
    description: payload.description,
    iconId: applicationIconIdFromKey(payload.iconKey),
    iconBackgroundColor: payload.iconBackgroundColor,
    sortOrder: payload.sortOrder,
    licenseState: applicationLicenseStateFromValue(payload.licenseState),
    favourite: Boolean(payload.favourite),
  };
}

/**
 * Loads the product catalog with this company's license states.
 *
 * @param requestInit optional abort signal
 * @returns catalog applications
 */
export async function fetchCompanyApplications(
  requestInit?: RequestInit
): Promise<ApplicationItem[]> {
  const payload = await httpOpzhub.get<CompanyApplicationsPayload>(APPS_PATH, requestInit);
  return (payload.applications ?? []).map(mapCompanyApplication);
}

/**
 * Licenses a catalog application for the signed-in company.
 *
 * @param applicationId catalog application id
 * @returns updated catalog item
 */
export async function licenseCompanyApplication(applicationId: string): Promise<ApplicationItem> {
  const payload = await httpOpzhub.post<CompanyApplicationPayload>(APPS_LICENSE_PATH, {
    applicationId,
  });
  return mapCompanyApplication(payload);
}

/**
 * Installs a licensed application onto the company dashboard.
 *
 * @param applicationId catalog application id
 * @returns updated catalog item
 */
export async function installCompanyApplication(applicationId: string): Promise<ApplicationItem> {
  const payload = await httpOpzhub.post<CompanyApplicationPayload>(APPS_INSTALL_PATH, {
    applicationId,
  });
  return mapCompanyApplication(payload);
}

/**
 * Uninstalls an installed application back to licensed.
 *
 * @param applicationId catalog application id
 * @returns updated catalog item
 */
export async function uninstallCompanyApplication(applicationId: string): Promise<ApplicationItem> {
  const payload = await httpOpzhub.post<CompanyApplicationPayload>(APPS_UNINSTALL_PATH, {
    applicationId,
  });
  return mapCompanyApplication(payload);
}

/**
 * Marks or unmarks an installed application as a dashboard favourite.
 *
 * @param applicationId catalog application id
 * @param favourite requested favourite value
 * @returns updated catalog item
 */
export async function setCompanyApplicationFavourite(
  applicationId: string,
  favourite: boolean
): Promise<ApplicationItem> {
  const payload = await httpOpzhub.post<CompanyApplicationPayload>(APPS_FAVOURITE_PATH, {
    applicationId,
    favourite,
  });
  return mapCompanyApplication(payload);
}
