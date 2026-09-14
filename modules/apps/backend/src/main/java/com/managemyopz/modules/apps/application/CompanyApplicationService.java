/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Company-scoped license and install transitions for catalog apps.
 */
package com.managemyopz.modules.apps.application;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.modules.apps.data.AppsDataConstants;
import com.managemyopz.modules.apps.data.CompanyApplicationRepository;
import com.managemyopz.modules.apps.domain.CompanyApplicationView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lists catalog applications using stored company_application license state.
 * Folder presence is only checked when licensing or installing.
 */
public class CompanyApplicationService {

    private final CompanyApplicationRepository repository;
    private final ApplicationFolderPresence folderPresence;

    /**
     * Creates the company application service.
     *
     * @param repository schema-driven application store
     * @param folderPresence apps/ folder checker
     */
    public CompanyApplicationService(
        CompanyApplicationRepository repository,
        ApplicationFolderPresence folderPresence
    ) {
        this.repository = repository;
        this.folderPresence = folderPresence;
    }

    /**
     * Lists catalog applications with this company's stored license state.
     *
     * @param session authenticated session
     * @return catalog views in launcher order
     */
    public List<CompanyApplicationView> listForSession(SessionAuthentication session) {
        UUID companyId = requireCompanyId(session);
        requireExistingCompany(companyId);
        return CompanyApplicationRepository.joinViews(
            repository.listCatalog(),
            repository.listCompanyApplications(companyId)
        );
    }

    /**
     * Licenses a catalog application for the session company when its apps folder exists.
     *
     * @param session authenticated session
     * @param applicationId catalog application id
     * @return updated view
     */
    public CompanyApplicationView license(SessionAuthentication session, String applicationId) {
        UUID companyId = requireCompanyId(session);
        requireExistingCompany(companyId);
        Row catalogRow = requireCatalog(applicationId);
        requireFolder(catalogRow);
        UUID catalogId = UUID.fromString(stringValue(catalogRow.get(AppsDataConstants.ID_KEY)));
        Row companyRow = repository.findCompanyApplication(companyId, catalogId).orElse(null);
        if (companyRow == null) {
            companyRow = repository.insertCompanyApplication(
                newCompanyRow(companyId, catalogId, AppsDataConstants.STATE_LICENSED));
        } else if (AppsDataConstants.STATE_UNLICENSED.equals(companyRow.getString(AppsDataConstants.LICENSE_STATE_KEY))) {
            companyRow = repository.updateLicenseState(
                UUID.fromString(stringValue(companyRow.get(AppsDataConstants.ID_KEY))),
                AppsDataConstants.STATE_LICENSED
            );
        }
        return CompanyApplicationRepository.toView(catalogRow, companyRow);
    }

    /**
     * Installs a licensed application so it appears on the company dashboard.
     *
     * @param session authenticated session
     * @param applicationId catalog application id
     * @return updated view
     */
    public CompanyApplicationView install(SessionAuthentication session, String applicationId) {
        UUID companyId = requireCompanyId(session);
        requireExistingCompany(companyId);
        Row catalogRow = requireCatalog(applicationId);
        requireFolder(catalogRow);
        UUID catalogId = UUID.fromString(stringValue(catalogRow.get(AppsDataConstants.ID_KEY)));
        Row companyRow = repository.findCompanyApplication(companyId, catalogId)
            .orElseThrow(() -> DataClientException.conflict("Application must be licensed before it can be installed."));
        String currentState = companyRow.getString(AppsDataConstants.LICENSE_STATE_KEY);
        if (AppsDataConstants.STATE_INSTALLED.equals(currentState)) {
            return CompanyApplicationRepository.toView(catalogRow, companyRow);
        }
        if (!AppsDataConstants.STATE_LICENSED.equals(currentState)) {
            throw DataClientException.conflict("Application must be licensed before it can be installed.");
        }
        Row updated = repository.updateLicenseState(
            UUID.fromString(stringValue(companyRow.get(AppsDataConstants.ID_KEY))),
            AppsDataConstants.STATE_INSTALLED
        );
        return CompanyApplicationRepository.toView(catalogRow, updated);
    }

    /**
     * Uninstalls an installed application back to licensed for this company.
     *
     * @param session authenticated session
     * @param applicationId catalog application id
     * @return updated view
     */
    public CompanyApplicationView uninstall(SessionAuthentication session, String applicationId) {
        UUID companyId = requireCompanyId(session);
        requireExistingCompany(companyId);
        Row catalogRow = requireCatalog(applicationId);
        UUID catalogId = UUID.fromString(stringValue(catalogRow.get(AppsDataConstants.ID_KEY)));
        Row companyRow = repository.findCompanyApplication(companyId, catalogId)
            .orElseThrow(() -> DataClientException.conflict("Application is not installed."));
        if (!AppsDataConstants.STATE_INSTALLED.equals(companyRow.getString(AppsDataConstants.LICENSE_STATE_KEY))) {
            throw DataClientException.conflict("Application is not installed.");
        }
        Row updated = repository.updateLicenseStateAndFavourite(
            UUID.fromString(stringValue(companyRow.get(AppsDataConstants.ID_KEY))),
            AppsDataConstants.STATE_LICENSED,
            false
        );
        return CompanyApplicationRepository.toView(catalogRow, updated);
    }

    /**
     * Marks or unmarks an installed application as a dashboard favourite.
     *
     * @param session authenticated session
     * @param applicationId catalog application id
     * @param favourite new favourite value
     * @return updated view
     */
    public CompanyApplicationView setFavourite(
        SessionAuthentication session,
        String applicationId,
        boolean favourite
    ) {
        UUID companyId = requireCompanyId(session);
        requireExistingCompany(companyId);
        Row catalogRow = requireCatalog(applicationId);
        UUID catalogId = UUID.fromString(stringValue(catalogRow.get(AppsDataConstants.ID_KEY)));
        Row companyRow = repository.findCompanyApplication(companyId, catalogId)
            .orElseThrow(() -> DataClientException.notFound("Unknown application."));
        if (!AppsDataConstants.STATE_INSTALLED.equals(companyRow.getString(AppsDataConstants.LICENSE_STATE_KEY))) {
            throw DataClientException.conflict("Only installed applications can be marked as favourite.");
        }
        Row updated = repository.updateFavourite(
            UUID.fromString(stringValue(companyRow.get(AppsDataConstants.ID_KEY))),
            favourite
        );
        return CompanyApplicationRepository.toView(catalogRow, updated);
    }

    private Row requireCatalog(String applicationId) {
        UUID catalogId = parseUuid(applicationId, "Unknown application.");
        return repository.findCatalogById(catalogId)
            .orElseThrow(() -> DataClientException.notFound("Unknown application."));
    }

    private void requireFolder(Row catalogRow) {
        String appKey = stringValue(catalogRow.get(AppsDataConstants.APP_KEY));
        if (!folderPresence.exists(appKey)) {
            throw DataClientException.conflict("Application cannot be licensed until its apps folder exists.");
        }
    }

    private static Map<String, Object> newCompanyRow(UUID companyId, UUID catalogId, String licenseState) {
        Map<String, Object> row = new HashMap<>();
        row.put(AppsDataConstants.ID_KEY, UUID.randomUUID());
        row.put(AppsDataConstants.COMPANY_ID_KEY, companyId);
        row.put(AppsDataConstants.APPLICATION_ID_KEY, catalogId);
        row.put(AppsDataConstants.LICENSE_STATE_KEY, licenseState);
        row.put(AppsDataConstants.FAVOURITE_KEY, Boolean.FALSE);
        return row;
    }

    private UUID requireCompanyId(SessionAuthentication session) {
        if (session == null || session.getCompanyId() == null || session.getCompanyId().isBlank()) {
            throw DataClientException.notFound("No company is linked to this account.");
        }
        return parseUuid(session.getCompanyId(), "No company is linked to this account.");
    }

    private void requireExistingCompany(UUID companyId) {
        if (!repository.companyExists(companyId)) {
            throw DataClientException.notFound("No company is linked to this account.");
        }
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw DataClientException.notFound(message);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
