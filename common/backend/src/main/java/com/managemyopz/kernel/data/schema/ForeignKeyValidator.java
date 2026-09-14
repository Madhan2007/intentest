/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Schema-driven existence checks for foreign keys Postgres cannot enforce.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.client.DataClientRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces YAML {@code foreign_keys} that cannot be (or are not) Postgres
 * constraints: {@code enforce: app}, and any relationship whose two tables live
 * on different named databases. Lookups use {@link SchemaRegistry} plus
 * {@link GenericSqlBuilder} — never table-specific SQL.
 */
@Component
public class ForeignKeyValidator {

    private static final String FOREIGN_KEY_CODE = "foreign_key";

    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;

    /**
     * Creates the schema-driven foreign-key write checker.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     */
    public ForeignKeyValidator(SchemaRegistry schemaRegistry, DataClientRegistry dataClientRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
    }

    /**
     * Checks payload values against referenced tables when app-level enforcement
     * applies. Absent or null values are skipped so nullable columns and patches
     * that omit the field remain valid.
     *
     * @param schema entity being written
     * @param payload create row or patch map
     * @param isPatch unused; presence is determined per field
     * @return field errors for values that do not exist on the referenced table
     */
    public List<SchemaValidationException.FieldError> validate(
            EntitySchema schema, Map<String, Object> payload, boolean isPatch) {
        if (schema.foreignKeys().isEmpty()) {
            return List.of();
        }
        List<SchemaValidationException.FieldError> errors = new ArrayList<>();
        for (ForeignKeyDefinition foreignKey : schema.foreignKeys()) {
            if (!payload.containsKey(foreignKey.column())) {
                continue;
            }
            Object value = payload.get(foreignKey.column());
            if (value == null) {
                continue;
            }
            Optional<EntitySchema> referenced = schemaRegistry.findByTable(foreignKey.referencesTable());
            if (referenced.isEmpty()) {
                continue;
            }
            EntitySchema referencedSchema = referenced.get();
            if (foreignKey.effectiveEnforce(schema.database(), referencedSchema.database())
                    != ForeignKeyDefinition.Enforce.APP) {
                continue;
            }
            if (!referencedRowExists(referencedSchema, foreignKey.referencesColumn(), value)) {
                errors.add(new SchemaValidationException.FieldError(
                    foreignKey.column(),
                    FOREIGN_KEY_CODE,
                    "Unknown reference for field: " + foreignKey.column()
                ));
            }
        }
        return errors;
    }

    private boolean referencedRowExists(EntitySchema referencedSchema, String referencedColumn, Object value) {
        if (!referencedSchema.hasColumn(referencedColumn)) {
            return false;
        }
        GenericSqlBuilder.SqlCommand command = GenericSqlBuilder.exists(
            referencedSchema, referencedColumn, value);
        return !dataClientRegistry.resolve(referencedSchema)
            .queryRaw(command.sql(), command.params())
            .isEmpty();
    }
}
