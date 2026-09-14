/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Schema-driven reads and writes for OPZHUB application tables.
 */
package com.managemyopz.modules.apps.data;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.ForeignKeyValidator;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.schema.SchemaValidationException;
import com.managemyopz.kernel.data.schema.web.FilterSpec;
import com.managemyopz.modules.apps.domain.CompanyApplicationView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads the product catalog and per-company license rows through
 * {@link GenericSqlBuilder}. Company existence is checked on OPZMAIN.
 */
public class CompanyApplicationRepository {

    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;
    private final ForeignKeyValidator foreignKeyValidator;

    /**
     * Creates the schema-driven application repository.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     * @param foreignKeyValidator app-level foreign-key checks
     */
    public CompanyApplicationRepository(
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry,
        ForeignKeyValidator foreignKeyValidator
    ) {
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
        this.foreignKeyValidator = foreignKeyValidator;
    }

    /**
     * Returns every catalog application in sort order.
     *
     * @return catalog rows
     */
    public List<Row> listCatalog() {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        FilterSpec filter = new FilterSpec(
            null,
            null,
            null,
            AppsDataConstants.SORT_ORDER_KEY,
            "asc",
            0,
            AppsDataConstants.CATALOG_PAGE_SIZE
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(
            schema, filter, AppsDataConstants.CATALOG_PAGE_SIZE, 0);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
    }

    /**
     * Finds one catalog application by its stable key.
     *
     * @param appKey unique application key
     * @return matching catalog row
     */
    public Optional<Row> findCatalogByAppKey(String appKey) {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        FilterSpec filter = new FilterSpec(
            Map.of(AppsDataConstants.APP_KEY, appKey),
            null,
            null,
            null,
            null,
            0,
            1
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(schema, filter, 1, 0);
        List<Row> rows = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Finds one catalog application by its product code.
     * Used as a secondary guard during seeding to prevent duplicate-key
     * violations when an {@code app_key} was renamed between releases but
     * the {@code product_code} was kept the same.
     *
     * @param productCode unique product code (e.g. {@code opz-004})
     * @return matching catalog row
     */
    public Optional<Row> findCatalogByProductCode(String productCode) {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        FilterSpec filter = new FilterSpec(
            Map.of(AppsDataConstants.PRODUCT_CODE_KEY, productCode),
            null,
            null,
            null,
            null,
            0,
            1
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(schema, filter, 1, 0);
        List<Row> rows = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Finds one catalog application by id.
     *
     * @param catalogId application_catalog.id
     * @return matching catalog row
     */
    public Optional<Row> findCatalogById(UUID catalogId) {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        SqlCommand command = GenericSqlBuilder.selectById(schema, catalogId);
        List<Row> rows = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Inserts a catalog application when the key is new.
     *
     * @param row catalog columns
     * @return inserted row
     */
    public Row insertCatalog(Map<String, Object> row) {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        SqlCommand command = GenericSqlBuilder.insert(schema, row);
        List<Row> inserted = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (inserted.isEmpty()) {
            throw DataClientException.conflict("Failed to insert application catalog row.");
        }
        return inserted.getFirst();
    }

    /**
     * Patches catalog columns for an existing application.
     *
     * @param id application_catalog.id
     * @param patch columns to update
     * @return updated row
     */
    public Row updateCatalog(UUID id, Map<String, Object> patch) {
        EntitySchema schema = requireSchema(AppsDataConstants.CATALOG_ENTITY);
        SqlCommand command = GenericSqlBuilder.update(schema, id, patch);
        List<Row> updated = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (updated.isEmpty()) {
            throw DataClientException.notFound("Unknown application catalog row.");
        }
        return updated.getFirst();
    }

    /**
     * Returns company application rows for one company.
     *
     * @param companyId company_information.id
     * @return company application rows
     */
    public List<Row> listCompanyApplications(UUID companyId) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        FilterSpec filter = new FilterSpec(
            Map.of(AppsDataConstants.COMPANY_ID_KEY, companyId),
            null,
            null,
            AppsDataConstants.APPLICATION_ID_KEY,
            "asc",
            0,
            AppsDataConstants.CATALOG_PAGE_SIZE
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(
            schema, filter, AppsDataConstants.CATALOG_PAGE_SIZE, 0);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
    }

    /**
     * Finds the company row for one catalog application.
     *
     * @param companyId company_information.id
     * @param applicationId application_catalog.id
     * @return matching company application row
     */
    public Optional<Row> findCompanyApplication(UUID companyId, UUID applicationId) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        FilterSpec filter = new FilterSpec(
            Map.of(
                AppsDataConstants.COMPANY_ID_KEY, companyId,
                AppsDataConstants.APPLICATION_ID_KEY, applicationId
            ),
            null,
            null,
            null,
            null,
            0,
            1
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(schema, filter, 1, 0);
        List<Row> rows = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Inserts a company application row after app-level foreign-key checks.
     *
     * @param row company application columns
     * @return inserted row
     */
    public Row insertCompanyApplication(Map<String, Object> row) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        List<SchemaValidationException.FieldError> errors = foreignKeyValidator.validate(schema, row, false);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException(errors);
        }
        SqlCommand command = GenericSqlBuilder.insert(schema, row);
        List<Row> inserted = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (inserted.isEmpty()) {
            throw DataClientException.conflict("Failed to insert company application row.");
        }
        return inserted.getFirst();
    }

    /**
     * Updates license_state for one company application row.
     *
     * @param id company_application.id
     * @param licenseState new state
     * @return updated row
     */
    public Row updateLicenseState(UUID id, String licenseState) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        SqlCommand command = GenericSqlBuilder.update(
            schema, id, Map.of(AppsDataConstants.LICENSE_STATE_KEY, licenseState));
        List<Row> updated = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (updated.isEmpty()) {
            throw DataClientException.notFound("Unknown company application.");
        }
        return updated.getFirst();
    }

    /**
     * Updates favourite for one company application row.
     *
     * @param id company_application.id
     * @param favourite new favourite value
     * @return updated row
     */
    public Row updateFavourite(UUID id, boolean favourite) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        SqlCommand command = GenericSqlBuilder.update(
            schema, id, Map.of(AppsDataConstants.FAVOURITE_KEY, favourite));
        List<Row> updated = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (updated.isEmpty()) {
            throw DataClientException.notFound("Unknown company application.");
        }
        return updated.getFirst();
    }

    /**
     * Updates license_state and favourite for one company application row.
     *
     * @param id company_application.id
     * @param licenseState new state
     * @param favourite new favourite value
     * @return updated row
     */
    public Row updateLicenseStateAndFavourite(UUID id, String licenseState, boolean favourite) {
        EntitySchema schema = requireSchema(AppsDataConstants.COMPANY_APPLICATION_ENTITY);
        SqlCommand command = GenericSqlBuilder.update(
            schema,
            id,
            Map.of(
                AppsDataConstants.LICENSE_STATE_KEY, licenseState,
                AppsDataConstants.FAVOURITE_KEY, favourite
            )
        );
        List<Row> updated = dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params());
        if (updated.isEmpty()) {
            throw DataClientException.notFound("Unknown company application.");
        }
        return updated.getFirst();
    }

    /**
     * Returns company ids from OPZMAIN.
     *
     * @return company_information ids
     */
    public List<UUID> listCompanyIds() {
        Optional<EntitySchema> schema = schemaRegistry.find(AppsDataConstants.COMPANY_ENTITY);
        if (schema.isEmpty()) {
            return List.of();
        }
        FilterSpec filter = new FilterSpec(
            null,
            null,
            null,
            AppsDataConstants.ID_KEY,
            "asc",
            0,
            AppsDataConstants.CATALOG_PAGE_SIZE
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(
            schema.get(), filter, AppsDataConstants.CATALOG_PAGE_SIZE, 0);
        List<Row> rows = dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params());
        List<UUID> companyIds = new ArrayList<>();
        for (Row row : rows) {
            Object companyId = row.get(AppsDataConstants.ID_KEY);
            if (companyId != null) {
                companyIds.add(UUID.fromString(companyId.toString()));
            }
        }
        return List.copyOf(companyIds);
    }

    /**
     * Confirms the company exists on OPZMAIN.
     *
     * @param companyId company_information.id
     * @return true when the company row exists
     */
    public boolean companyExists(UUID companyId) {
        Optional<EntitySchema> schema = schemaRegistry.find(AppsDataConstants.COMPANY_ENTITY);
        if (schema.isEmpty()) {
            return false;
        }
        SqlCommand command = GenericSqlBuilder.exists(schema.get(), AppsDataConstants.ID_KEY, companyId);
        return !dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params()).isEmpty();
    }

    /**
     * Joins catalog rows with stored company license state into launcher views.
     * Missing company rows are unlicensed. Installed, then licensed, then unlicensed.
     *
     * @param catalogRows catalog table rows
     * @param companyRows company application rows
     * @return views in launcher order
     */
    public static List<CompanyApplicationView> joinViews(
        List<Row> catalogRows,
        List<Row> companyRows
    ) {
        Map<String, Row> companyByApplicationId = new HashMap<>();
        for (Row companyRow : companyRows) {
            Object applicationId = companyRow.get(AppsDataConstants.APPLICATION_ID_KEY);
            if (applicationId != null) {
                companyByApplicationId.put(applicationId.toString(), companyRow);
            }
        }
        List<CompanyApplicationView> views = new ArrayList<>();
        for (Row catalogRow : catalogRows) {
            Object catalogId = catalogRow.get(AppsDataConstants.ID_KEY);
            if (catalogId == null) {
                continue;
            }
            views.add(toView(catalogRow, companyByApplicationId.get(catalogId.toString())));
        }
        views.sort(Comparator
            .comparingInt((CompanyApplicationView view) -> licenseRank(view.licenseState()))
            .thenComparingInt(CompanyApplicationView::sortOrder));
        return List.copyOf(views);
    }

    /**
     * Maps one joined pair into a view.
     *
     * @param catalogRow catalog table row
     * @param companyRow company application row
     * @return view for the launcher and dashboard
     */
    public static CompanyApplicationView toView(Row catalogRow, Row companyRow) {
        if (companyRow == null) {
            return toView(catalogRow, null, AppsDataConstants.STATE_UNLICENSED, false);
        }
        return toView(
            catalogRow,
            companyRow,
            stringValue(companyRow.get(AppsDataConstants.LICENSE_STATE_KEY)),
            booleanValue(companyRow.get(AppsDataConstants.FAVOURITE_KEY))
        );
    }

    private static CompanyApplicationView toView(
        Row catalogRow,
        Row companyRow,
        String licenseState,
        boolean favourite
    ) {
        String companyApplicationId = companyRow == null
            ? ""
            : stringValue(companyRow.get(AppsDataConstants.ID_KEY));
        return new CompanyApplicationView(
            stringValue(catalogRow.get(AppsDataConstants.ID_KEY)),
            companyApplicationId,
            catalogRow.getString(AppsDataConstants.APP_KEY),
            stringValue(catalogRow.get(AppsDataConstants.PRODUCT_CODE_KEY)),
            stringValue(catalogRow.get(AppsDataConstants.CATEGORY_KEY)),
            catalogRow.getString(AppsDataConstants.DISPLAY_NAME_KEY),
            catalogRow.getString(AppsDataConstants.DESCRIPTION_KEY),
            catalogRow.getString(AppsDataConstants.ICON_KEY),
            catalogRow.getString(AppsDataConstants.ICON_BACKGROUND_COLOR_KEY),
            intValue(catalogRow.get(AppsDataConstants.SORT_ORDER_KEY)),
            licenseState,
            favourite
        );
    }

    private static int licenseRank(String licenseState) {
        if (AppsDataConstants.STATE_INSTALLED.equals(licenseState)) {
            return 0;
        }
        if (AppsDataConstants.STATE_LICENSED.equals(licenseState)) {
            return 1;
        }
        return 2;
    }

    private EntitySchema requireSchema(String entity) {
        return schemaRegistry.find(entity)
            .orElseThrow(() -> DataClientException.unavailable("Unknown entity: " + entity, null));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
