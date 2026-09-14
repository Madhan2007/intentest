/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Loads company applications and dashboard/launcher view state.
 */
import { useCallback, useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { isAbortError } from "@kernel/api/abortError";
import { abortableWait } from "./abortableWait";
import {
  buildDashboardFilterTabs,
  type ApplicationFilterId,
  type ApplicationFilterTab,
  type ApplicationItem,
} from "./applicationCatalog";
import {
  fetchCompanyApplications,
  installCompanyApplication,
  licenseCompanyApplication,
  setCompanyApplicationFavourite,
  uninstallCompanyApplication,
} from "./companyApplicationsApi";
import {
  DASHBOARD_EMPTY_APPLICATIONS,
  DASHBOARD_EMPTY_FILTERED_APPLICATIONS,
  DASHBOARD_FAVOURITE_ERROR,
  DASHBOARD_INSTALL_ERROR,
  DASHBOARD_LAUNCHER_LOAD_ERROR,
  DASHBOARD_LICENSE_ERROR,
  DASHBOARD_UNINSTALL_ERROR,
  FILTER_ALL,
  INSTALL_PROGRESS_MIN_MS,
  LICENSE_STATE_INSTALLED,
  LICENSE_STATE_LICENSED,
} from "./dashboardConstants";
import { filterInstalledApplications } from "./filterApplicationCatalog";

export interface DashboardCatalogState {
  searchQuery: string;
  activeFilterId: ApplicationFilterId;
  filterTabs: ApplicationFilterTab[];
  launcherApplications: ApplicationItem[];
  visibleApplications: ApplicationItem[];
  emptyMessage: string;
  isCatalogLoading: boolean;
  catalogError: string | null;
  installingApplicationId: string | null;
  setSearchQuery: (searchQuery: string) => void;
  setActiveFilterId: (filterId: ApplicationFilterId) => void;
  licenseApplication: (applicationId: string) => Promise<void>;
  installApplication: (applicationId: string) => Promise<void>;
  uninstallApplication: (applicationId: string) => Promise<void>;
  toggleFavourite: (applicationId: string, favourite: boolean) => Promise<void>;
}

/**
 * Loads the company catalog and keeps dashboard search plus install progress.
 *
 * @returns catalog view model for the dashboard page
 */
export function useDashboardCatalog(): DashboardCatalogState {
  const [searchQuery, setSearchQuery] = useState("");
  const [activeFilterId, setActiveFilterId] = useState<ApplicationFilterId>(FILTER_ALL);
  const [applications, setApplications] = useState<ApplicationItem[]>([]);
  const [isCatalogLoading, setIsCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [installingApplicationId, setInstallingApplicationId] = useState<string | null>(null);
  const deferredSearchQuery = useDeferredValue(searchQuery);
  const isMountedRef = useRef(true);
  const applicationsRef = useRef<ApplicationItem[]>([]);
  const installingRef = useRef<string | null>(null);
  const waitAbortRef = useRef<AbortController | null>(null);
  applicationsRef.current = applications;

  useEffect(() => {
    isMountedRef.current = true;
    const abortController = new AbortController();
    setIsCatalogLoading(true);
    fetchCompanyApplications({ signal: abortController.signal })
      .then((loadedApplications) => {
        if (!isMountedRef.current) {
          return;
        }
        setApplications(loadedApplications);
        setCatalogError(null);
      })
      .catch((error: unknown) => {
        if (!isMountedRef.current || isAbortError(error)) {
          return;
        }
        setApplications([]);
        setCatalogError(error instanceof Error ? error.message : DASHBOARD_LAUNCHER_LOAD_ERROR);
      })
      .finally(() => {
        if (isMountedRef.current) {
          setIsCatalogLoading(false);
        }
      });
    return () => {
      isMountedRef.current = false;
      abortController.abort();
      waitAbortRef.current?.abort();
    };
  }, []);

  const replaceApplication = useCallback((updatedApplication: ApplicationItem) => {
    setApplications((currentApplications) =>
      currentApplications.map((application) =>
        application.id === updatedApplication.id ? updatedApplication : application
      )
    );
  }, []);

  const applyCatalogUpdate = useCallback(
    async (request: () => Promise<ApplicationItem>, fallbackMessage: string) => {
      try {
        const updatedApplication = await request();
        if (!isMountedRef.current) {
          return;
        }
        setCatalogError(null);
        replaceApplication(updatedApplication);
      } catch (error: unknown) {
        if (!isMountedRef.current) {
          return;
        }
        setCatalogError(error instanceof Error ? error.message : fallbackMessage);
      }
    },
    [replaceApplication]
  );

  const licenseApplication = useCallback(
    async (applicationId: string) => {
      await applyCatalogUpdate(() => licenseCompanyApplication(applicationId), DASHBOARD_LICENSE_ERROR);
    },
    [applyCatalogUpdate]
  );

  const installApplication = useCallback(
    async (applicationId: string) => {
      if (installingRef.current) {
        return;
      }
      const targetApplication = applicationsRef.current.find(
        (application) => application.id === applicationId
      );
      if (!targetApplication || targetApplication.licenseState !== LICENSE_STATE_LICENSED) {
        return;
      }
      installingRef.current = applicationId;
      const startedAt = Date.now();
      const waitAbortController = new AbortController();
      waitAbortRef.current = waitAbortController;
      setInstallingApplicationId(applicationId);
      try {
        const updatedApplication = await installCompanyApplication(applicationId);
        const elapsedMs = Date.now() - startedAt;
        if (elapsedMs < INSTALL_PROGRESS_MIN_MS) {
          await abortableWait(INSTALL_PROGRESS_MIN_MS - elapsedMs, waitAbortController.signal);
        }
        if (!isMountedRef.current) {
          return;
        }
        setCatalogError(null);
        replaceApplication(updatedApplication);
      } catch (error: unknown) {
        if (isMountedRef.current) {
          setCatalogError(error instanceof Error ? error.message : DASHBOARD_INSTALL_ERROR);
        }
      } finally {
        waitAbortController.abort();
        if (waitAbortRef.current === waitAbortController) {
          waitAbortRef.current = null;
        }
        installingRef.current = null;
        if (isMountedRef.current) {
          setInstallingApplicationId(null);
        }
      }
    },
    [replaceApplication]
  );

  const uninstallApplication = useCallback(
    async (applicationId: string) => {
      const targetApplication = applicationsRef.current.find(
        (application) => application.id === applicationId
      );
      if (!targetApplication || targetApplication.licenseState !== LICENSE_STATE_INSTALLED) {
        return;
      }
      await applyCatalogUpdate(
        () => uninstallCompanyApplication(applicationId),
        DASHBOARD_UNINSTALL_ERROR
      );
    },
    [applyCatalogUpdate]
  );

  const toggleFavourite = useCallback(
    async (applicationId: string, favourite: boolean) => {
      await applyCatalogUpdate(
        () => setCompanyApplicationFavourite(applicationId, favourite),
        DASHBOARD_FAVOURITE_ERROR
      );
    },
    [applyCatalogUpdate]
  );

  const filterTabs = useMemo(() => buildDashboardFilterTabs(applications), [applications]);

  const visibleApplications = useMemo(
    () =>
      filterInstalledApplications(
        applications,
        deferredSearchQuery,
        activeFilterId,
        installingApplicationId
      ),
    [activeFilterId, applications, deferredSearchQuery, installingApplicationId]
  );

  const emptyMessage = useMemo(() => {
    const hasInstalledApplication = applications.some(
      (application) => application.licenseState === LICENSE_STATE_INSTALLED
    );
    if (!hasInstalledApplication && !installingApplicationId) {
      return DASHBOARD_EMPTY_APPLICATIONS;
    }
    return DASHBOARD_EMPTY_FILTERED_APPLICATIONS;
  }, [applications, installingApplicationId]);

  return {
    searchQuery,
    activeFilterId,
    filterTabs,
    launcherApplications: applications,
    visibleApplications,
    emptyMessage,
    isCatalogLoading,
    catalogError,
    installingApplicationId,
    setSearchQuery,
    setActiveFilterId,
    licenseApplication,
    installApplication,
    uninstallApplication,
    toggleFavourite,
  };
}
