/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Startup schema reconciliation: for every {@link EntitySchema} known to
 * {@link SchemaRegistry}, create the table if missing, otherwise add only the
 * columns/indexes/foreign keys the live table is missing. Never drops or alters
 * an existing column/table — see {@link DdlBuilder}'s structural guarantee — so
 * a schema definition can safely evolve release over release without losing data.
 * No-ops entirely on {@code db.type != postgres}.
 *
 * <p>Runs in two passes across every entity — all tables/columns/indexes first,
 * then all foreign keys — so a cross-entity FK (e.g. company_information ->
 * company_license) converges correctly in one boot regardless of the order
 * {@link SchemaRegistry#all()} happens to return entities in.
 *
 * <p>Ordered to run before every other {@code ApplicationRunner}: identity's own
 * admin-bootstrap runner queries {@code id_user} immediately on boot, and that
 * table's creation now depends on this reconciler having already run.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaReconciler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaReconciler.class);
    private static final String POSTGRES_DB_TYPE = "postgres";

    private final SchemaRegistry schemaRegistry;
    private final SchemaIntrospector introspector;
    private final DataClientRegistry dataClientRegistry;
    private final PlatformProperties platformProperties;

    public SchemaReconciler(SchemaRegistry schemaRegistry, SchemaIntrospector introspector,
                             DataClientRegistry dataClientRegistry, PlatformProperties platformProperties) {
        this.schemaRegistry = schemaRegistry;
        this.introspector = introspector;
        this.dataClientRegistry = dataClientRegistry;
        this.platformProperties = platformProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String dbType = platformProperties.getDb().getType();
        if (!POSTGRES_DB_TYPE.equals(dbType)) {
            log.info("Schema reconciliation skipped (db.type={})", dbType);
            return;
        }
        Collection<EntitySchema> schemas = schemaRegistry.all();

        for (EntitySchema schema : schemas) {
            try {
                reconcileTableAndIndexes(schema);
            } catch (Exception e) {
                log.error("Schema reconciliation (table/columns) failed for entity {}: {}",
                    schema.entity(), e.getMessage(), e);
            }
        }
        for (EntitySchema schema : schemas) {
            try {
                reconcileForeignKeys(schema);
            } catch (Exception e) {
                log.error("Schema reconciliation (foreign keys) failed for entity {}: {}",
                    schema.entity(), e.getMessage(), e);
            }
        }
    }

    private void reconcileTableAndIndexes(EntitySchema schema) {
        DataClient client = dataClientRegistry.resolve(schema);
        SchemaIntrospector.LiveTable live = introspector.introspect(client, schema.table());

        if (!live.exists()) {
            client.executeRaw(DdlBuilder.createTable(schema), Map.of());
            for (IndexDefinition index : schema.indexes()) {
                client.executeRaw(DdlBuilder.createIndex(schema, index), Map.of());
            }
            log.info("Created table {} (entity {}, v{})", schema.table(), schema.entity(), schema.version());
            return;
        }

        int addedColumns = 0;
        for (ColumnDefinition column : schema.columns()) {
            if (!live.columns().contains(column.name())) {
                client.executeRaw(DdlBuilder.addColumn(schema, column), Map.of());
                addedColumns++;
            }
        }
        int addedIndexes = 0;
        for (IndexDefinition index : schema.indexes()) {
            if (!live.indexes().contains(index.name())) {
                client.executeRaw(DdlBuilder.createIndex(schema, index), Map.of());
                addedIndexes++;
            }
        }
        if (addedColumns > 0 || addedIndexes > 0) {
            log.info("Reconciled table {}: +{} columns, +{} indexes", schema.table(), addedColumns, addedIndexes);
        }
    }

    private void reconcileForeignKeys(EntitySchema schema) {
        if (schema.foreignKeys().isEmpty()) {
            return;
        }
        DataClient client = dataClientRegistry.resolve(schema);
        SchemaIntrospector.LiveTable live = introspector.introspect(client, schema.table());

        int addedForeignKeys = 0;
        for (ForeignKeyDefinition fk : schema.foreignKeys()) {
            if (!isPostgresEnforced(schema, fk)) {
                continue;
            }
            boolean missing = !live.foreignKeys().contains(DdlBuilder.foreignKeyConstraintName(schema, fk));
            if (missing && applyForeignKey(client, schema, fk)) {
                addedForeignKeys++;
            }
        }
        if (addedForeignKeys > 0) {
            log.info("Reconciled table {}: +{} foreign keys", schema.table(), addedForeignKeys);
        }
    }

    /**
     * Postgres can only attach a constraint when both tables share a physical
     * database. Cross-database YAML foreign keys are enforced by
     * {@link ForeignKeyValidator} instead.
     *
     * @param schema declaring entity
     * @param foreignKey YAML foreign-key row
     * @return true when {@code ALTER TABLE ... ADD CONSTRAINT} should run
     */
    private boolean isPostgresEnforced(EntitySchema schema, ForeignKeyDefinition foreignKey) {
        Optional<EntitySchema> referenced = schemaRegistry.findByTable(foreignKey.referencesTable());
        if (referenced.isPresent()) {
            return foreignKey.effectiveEnforce(schema.database(), referenced.get().database())
                == ForeignKeyDefinition.Enforce.POSTGRES;
        }
        return foreignKey.enforce() == ForeignKeyDefinition.Enforce.POSTGRES;
    }

    private boolean applyForeignKey(DataClient client, EntitySchema schema, ForeignKeyDefinition fk) {
        try {
            client.executeRaw(DdlBuilder.addForeignKey(schema, fk), Map.of());
            return true;
        } catch (DataClientException e) {
            log.warn("Could not add foreign key {} on {}: {} (existing rows may violate it; add manually after cleanup)",
                DdlBuilder.foreignKeyConstraintName(schema, fk), schema.table(), e.getMessage());
            return false;
        }
    }
}
