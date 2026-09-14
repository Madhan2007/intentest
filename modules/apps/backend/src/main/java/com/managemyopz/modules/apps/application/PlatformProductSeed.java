/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-13
 * Description: Records which product this deployment was packaged for.
 */
package com.managemyopz.modules.apps.application;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.schema.web.FilterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code platform/config/product.yaml} — written exactly once by
 * {@code infra/wrappers/package-product.sh --product <key>} — and stores
 * that value into a single-row marker table in <b>both</b> OPZMAIN
 * ({@code platform_product}) and OPZHUB ({@code company_active_product})
 * (doc 35 §3.5). This is a filesystem read followed by a DB write, never a
 * command-line argument: {@code --product} is chosen exactly once, at
 * packaging time, by a human running the packaging script. Every other
 * component — this seed, the running application, the operator CLI — finds
 * out which product it is by reading the database, not by being told again.
 * <p>
 * When {@code platform/config/product.yaml} does not exist (running
 * directly from the vendor monorepo without ever packaging), the product
 * defaults to {@code managemyopz}, matching every other default in this
 * codebase.
 */
public class PlatformProductSeed {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformProductSeed.class);

    private static final String PRODUCT_STAMP_FILE = "platform/config/product.yaml";
    private static final String DEFAULT_PRODUCT_KEY = "managemyopz";
    private static final String PRODUCT_KEY_FIELD = "product_key";

    private static final String PLATFORM_PRODUCT_ENTITY = "platform_product";
    private static final String COMPANY_ACTIVE_PRODUCT_ENTITY = "company_active_product";
    private static final String SINGLETON_KEY_COLUMN = "singleton_key";
    private static final String SINGLETON_KEY_VALUE = "default";
    private static final String ACTIVATED_AT_COLUMN = "activated_at";

    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;

    /**
     * Creates the platform product seed.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     */
    public PlatformProductSeed(SchemaRegistry schemaRegistry, DataClientRegistry dataClientRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
    }

    /**
     * Reads the product stamp and upserts the singleton row into every
     * database that has the marker schema registered. Missing schemas
     * (e.g. a minimal test profile) are skipped with a warning, never fatal.
     */
    public void seed() {
        String productKey = readProductStampOrDefault();
        LOGGER.info("Platform product seed: active product = {}", productKey);
        upsertSingleton(PLATFORM_PRODUCT_ENTITY, productKey);
        upsertSingleton(COMPANY_ACTIVE_PRODUCT_ENTITY, productKey);
    }

    private String readProductStampOrDefault() {
        Path stampFile = Path.of(PRODUCT_STAMP_FILE);
        if (!Files.isRegularFile(stampFile)) {
            LOGGER.info(
                "Platform product seed: {} not found; defaulting to '{}' (unpackaged / monorepo run)",
                PRODUCT_STAMP_FILE, DEFAULT_PRODUCT_KEY
            );
            return DEFAULT_PRODUCT_KEY;
        }
        try {
            Map<String, Object> document = new Yaml().load(Files.newInputStream(stampFile));
            if (document == null) {
                return DEFAULT_PRODUCT_KEY;
            }
            Object value = document.get(PRODUCT_KEY_FIELD);
            String productKey = value == null ? "" : value.toString().trim();
            return productKey.isBlank() ? DEFAULT_PRODUCT_KEY : productKey;
        } catch (IOException exception) {
            LOGGER.warn("Platform product seed: failed to read {}; defaulting to '{}'",
                PRODUCT_STAMP_FILE, DEFAULT_PRODUCT_KEY, exception);
            return DEFAULT_PRODUCT_KEY;
        }
    }

    private void upsertSingleton(String entityName, String productKey) {
        Optional<EntitySchema> schema = schemaRegistry.find(entityName);
        if (schema.isEmpty()) {
            LOGGER.warn("Platform product seed: {} schema not registered; skipped", entityName);
            return;
        }
        try {
            Optional<Row> existing = findSingleton(schema.get());
            if (existing.isPresent()) {
                if (productKey.equals(existing.get().getString(PRODUCT_KEY_FIELD))) {
                    return;
                }
                SqlCommand command = GenericSqlBuilder.update(
                    schema.get(),
                    SINGLETON_KEY_VALUE,
                    Map.of(PRODUCT_KEY_FIELD, productKey, ACTIVATED_AT_COLUMN, OffsetDateTime.now())
                );
                dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params());
                LOGGER.info("Platform product seed: {} updated to '{}'", entityName, productKey);
                return;
            }
            Map<String, Object> row = new HashMap<>();
            row.put(SINGLETON_KEY_COLUMN, SINGLETON_KEY_VALUE);
            row.put(PRODUCT_KEY_FIELD, productKey);
            SqlCommand command = GenericSqlBuilder.insert(schema.get(), row);
            dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params());
            LOGGER.info("Platform product seed: {} seeded with '{}'", entityName, productKey);
        } catch (DataClientException exception) {
            LOGGER.warn("Platform product seed: failed to upsert {}: {}", entityName, exception.getMessage());
        }
    }

    private Optional<Row> findSingleton(EntitySchema schema) {
        FilterSpec filter = new FilterSpec(
            Map.of(SINGLETON_KEY_COLUMN, SINGLETON_KEY_VALUE),
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
}
