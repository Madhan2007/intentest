/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Loads the product application catalog into OPZHUB at boot.
 */
package com.managemyopz.modules.apps.application;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.schema.web.FilterSpec;
import com.managemyopz.modules.apps.data.AppsDataConstants;
import com.managemyopz.modules.apps.data.CompanyApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Inserts missing {@code application_catalog} rows from the module seed YAML.
 * Existing {@code app_key} values are left unchanged. Runs after schema
 * reconciliation and only against PostgreSQL. Also seeds OPZMAIN's
 * {@code product_catalog} and backfills {@code product_key} everywhere,
 * cross-database, from {@code platform/catalog/products.yaml} (doc 35 §3) —
 * the same cross-module read/write pattern already used elsewhere in this
 * repository (e.g. {@code SchemaCompanyDirectory} reads OPZMAIN's
 * {@code company_information} from the identity module).
 */
public class ApplicationCatalogSeed {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationCatalogSeed.class);
    private static final String POSTGRES_DATABASE_TYPE = "postgres";
    private static final String PRODUCT_CATALOG_ENTITY = "product_catalog";
    private static final String PRODUCT_NAME_KEY = "product_name";
    private static final String DESCRIPTION_KEY = "description";
    private static final String LOGO_URI_KEY = "logo_uri";
    private static final String ICON_KEY = "icon_key";
    private static final String STATUS_KEY = "status";

    private final CompanyApplicationRepository repository;
    private final PlatformProperties platformProperties;
    private final ResourceLoader resourceLoader;
    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;

    /**
     * Creates the catalog seed loader.
     *
     * @param repository schema-driven application store
     * @param platformProperties resolved platform configuration
     * @param resourceLoader classpath resource loader
     * @param schemaRegistry loaded entity schemas (for OPZMAIN product_catalog)
     * @param dataClientRegistry named-database router
     */
    public ApplicationCatalogSeed(
        CompanyApplicationRepository repository,
        PlatformProperties platformProperties,
        ResourceLoader resourceLoader,
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry
    ) {
        this.repository = repository;
        this.platformProperties = platformProperties;
        this.resourceLoader = resourceLoader;
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
    }

    /**
     * Seeds catalog applications that are not already stored.
     */
    public void seed() {
        if (!POSTGRES_DATABASE_TYPE.equalsIgnoreCase(platformProperties.getDb().getType())) {
            LOGGER.info("Application catalog seed skipped (db.type={})", platformProperties.getDb().getType());
            return;
        }
        Resource resource = resourceLoader.getResource(AppsDataConstants.SEED_RESOURCE);
        if (!resource.exists()) {
            LOGGER.warn("Application catalog seed file is missing");
            return;
        }
        List<Map<String, Object>> applications = loadApplications(resource);
        int inserted = 0;
        int updated = 0;
        for (Map<String, Object> application : applications) {
            String appKey = stringValue(application.get(AppsDataConstants.APP_KEY));
            if (appKey.isBlank()) {
                continue;
            }
            var existing = repository.findCatalogByAppKey(appKey);
            if (existing.isEmpty()) {
                // Secondary guard: the app_key may have been renamed between releases
                // while the product_code stayed the same. Without this check an INSERT
                // would hit the unique constraint on product_code.
                String productCode = stringValue(application.get(AppsDataConstants.PRODUCT_CODE_KEY));
                if (!productCode.isBlank()) {
                    existing = repository.findCatalogByProductCode(productCode);
                }
            }
            if (existing.isEmpty()) {
                repository.insertCatalog(catalogRow(application, appKey));
                inserted++;
                continue;
            }
            if (backfillCatalog(existing.get(), application)) {
                updated++;
            }
        }
        LOGGER.info("Application catalog seed complete (inserted={}, updated={})", inserted, updated);
        backfillProductKeys();
    }

    /**
     * Backfills {@code application_catalog.product_key} from the product →
     * app-list mapping in {@code platform/catalog/products.yaml} (doc 35 §3).
     * Runs after the main seed loop so every app_key has a catalog row to
     * patch. An app_key not listed under any product keeps product_key
     * unset and is excluded from every product-scoped package build.
     */
    private void backfillProductKeys() {
        Resource resource = resourceLoader.getResource(AppsDataConstants.PRODUCTS_CATALOG_RESOURCE);
        if (!resource.exists()) {
            LOGGER.warn("Product catalog file is missing; skipping product_key backfill");
            return;
        }
        List<Map<String, Object>> products = loadProducts(resource);
        seedProductCatalog(products);
        int backfilled = 0;
        for (Map<String, Object> product : products) {
            String productKey = stringValue(product.get(AppsDataConstants.PRODUCT_KEY));
            if (productKey.isBlank()) {
                continue;
            }
            for (Object appKeyValue : appKeysOf(product)) {
                String appKey = stringValue(appKeyValue);
                if (appKey.isBlank()) {
                    continue;
                }
                var existing = repository.findCatalogByAppKey(appKey);
                if (existing.isEmpty()) {
                    continue;
                }
                Object currentProductKey = existing.get().get(AppsDataConstants.PRODUCT_KEY);
                if (productKey.equals(stringValue(currentProductKey))) {
                    continue;
                }
                Object catalogId = existing.get().get(AppsDataConstants.ID_KEY);
                repository.updateCatalog(
                    UUID.fromString(catalogId.toString()),
                    Map.of(AppsDataConstants.PRODUCT_KEY, productKey)
                );
                backfilled++;
            }
        }
        LOGGER.info("Application catalog product_key backfill complete (updated={})", backfilled);
    }

    /**
     * Upserts OPZMAIN's {@code product_catalog} rows from products.yaml so
     * that {@code company_information.product_key} and
     * {@code application_catalog.product_key} always have a valid row to
     * reference. Must run before {@link #backfillProductKeys()} sets any
     * product_key value.
     *
     * @param products parsed products.yaml entries
     */
    private void seedProductCatalog(List<Map<String, Object>> products) {
        Optional<EntitySchema> schema = schemaRegistry.find(PRODUCT_CATALOG_ENTITY);
        if (schema.isEmpty()) {
            LOGGER.warn("product_catalog schema is not registered; skipping product catalog seed");
            return;
        }
        int inserted = 0;
        for (Map<String, Object> product : products) {
            String productKey = stringValue(product.get(AppsDataConstants.PRODUCT_KEY));
            if (productKey.isBlank()) {
                continue;
            }
            if (findProductByKey(schema.get(), productKey).isPresent()) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put(AppsDataConstants.PRODUCT_KEY, productKey);
            row.put(PRODUCT_NAME_KEY, stringValue(product.get(PRODUCT_NAME_KEY)));
            row.put(DESCRIPTION_KEY, stringValue(product.get(DESCRIPTION_KEY)));
            row.put(LOGO_URI_KEY, stringValue(product.get(LOGO_URI_KEY)));
            row.put(ICON_KEY, stringValue(product.get(ICON_KEY)));
            row.put(STATUS_KEY, stringValue(product.getOrDefault(STATUS_KEY, "active")));
            SqlCommand command = GenericSqlBuilder.insert(schema.get(), row);
            try {
                dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params());
                inserted++;
            } catch (DataClientException exception) {
                LOGGER.warn("Failed to insert product_catalog row for {}: {}", productKey, exception.getMessage());
            }
        }
        LOGGER.info("Product catalog seed complete (inserted={})", inserted);
    }

    private Optional<Row> findProductByKey(EntitySchema schema, String productKey) {
        FilterSpec filter = new FilterSpec(
            Map.of(AppsDataConstants.PRODUCT_KEY, productKey),
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

    @SuppressWarnings("unchecked")
    private static List<Object> appKeysOf(Map<String, Object> product) {
        Object apps = product.get(AppsDataConstants.PRODUCT_APPS_KEY);
        return apps instanceof List<?> list ? (List<Object>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadApplications(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> document = new Yaml().load(inputStream);
            if (document == null) {
                return List.of();
            }
            Object applications = document.get(AppsDataConstants.SEED_APPLICATIONS_KEY);
            if (!(applications instanceof List<?> list)) {
                return List.of();
            }
            return (List<Map<String, Object>>) list;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load application catalog seed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadProducts(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            Map<String, Object> document = new Yaml().load(inputStream);
            if (document == null) {
                return List.of();
            }
            Object products = document.get(AppsDataConstants.PRODUCTS_KEY);
            if (!(products instanceof List<?> list)) {
                return List.of();
            }
            return (List<Map<String, Object>>) list;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load product catalog", exception);
        }
    }

    private static Map<String, Object> catalogRow(Map<String, Object> application, String appKey) {
        Map<String, Object> row = new HashMap<>();
        row.put(AppsDataConstants.ID_KEY, UUID.randomUUID());
        row.put(AppsDataConstants.APP_KEY, appKey);
        row.put(AppsDataConstants.DISPLAY_NAME_KEY, stringValue(application.get(AppsDataConstants.DISPLAY_NAME_KEY)));
        row.put(AppsDataConstants.DESCRIPTION_KEY, stringValue(application.get(AppsDataConstants.DESCRIPTION_KEY)));
        row.put(AppsDataConstants.ICON_KEY, stringValue(application.get(AppsDataConstants.ICON_KEY)));
        row.put(
            AppsDataConstants.ICON_BACKGROUND_COLOR_KEY,
            stringValue(application.get(AppsDataConstants.ICON_BACKGROUND_COLOR_KEY))
        );
        row.put(AppsDataConstants.PRODUCT_CODE_KEY, stringValue(application.get(AppsDataConstants.PRODUCT_CODE_KEY)));
        row.put(AppsDataConstants.CATEGORY_KEY, stringValue(application.get(AppsDataConstants.CATEGORY_KEY)));
        row.put(AppsDataConstants.SORT_ORDER_KEY, intValue(application.get(AppsDataConstants.SORT_ORDER_KEY)));
        return row;
    }

    private boolean backfillCatalog(Row existing, Map<String, Object> application) {
        Map<String, Object> patch = new HashMap<>();
        if (isBlank(existing.get(AppsDataConstants.PRODUCT_CODE_KEY))) {
            patch.put(
                AppsDataConstants.PRODUCT_CODE_KEY,
                stringValue(application.get(AppsDataConstants.PRODUCT_CODE_KEY))
            );
        }
        if (isBlank(existing.get(AppsDataConstants.CATEGORY_KEY))) {
            patch.put(AppsDataConstants.CATEGORY_KEY, stringValue(application.get(AppsDataConstants.CATEGORY_KEY)));
        }
        if (patch.isEmpty()) {
            return false;
        }
        Object catalogId = existing.get(AppsDataConstants.ID_KEY);
        repository.updateCatalog(UUID.fromString(catalogId.toString()), patch);
        return true;
    }

    private static boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
