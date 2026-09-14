/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Closed set of column types the generic schema engine understands. Each maps to
 * exactly one Postgres DDL fragment and one Java bind type — a schema definition
 * can never supply a free-form SQL type string.
 */
public enum ColumnType {
    UUID("UUID", UUID.class),
    TEXT("TEXT", String.class),
    INTEGER("INTEGER", Integer.class),
    BIGINT("BIGINT", Long.class),
    DECIMAL("NUMERIC(%d,%d)", BigDecimal.class),
    BOOLEAN("BOOLEAN", Boolean.class),
    TIMESTAMPTZ("TIMESTAMPTZ", Instant.class),
    DATE("DATE", LocalDate.class),
    JSONB("JSONB", String.class),
    /** DDL-generation only — no runtime JDBC array-value binding exists in
     *  GenericSqlBuilder/GenericCrudRepository yet. Safe today only for entities
     *  that never write through the generic engine (e.g. identity's own id_user
     *  schema, which keeps its hand-written SQL commands for reads/writes). */
    TEXT_ARRAY("TEXT[]", String[].class);

    private static final int DEFAULT_PRECISION = 18;
    private static final int DEFAULT_SCALE = 4;

    private final String ddlTemplate;
    private final Class<?> javaType;

    ColumnType(String ddlTemplate, Class<?> javaType) {
        this.ddlTemplate = ddlTemplate;
        this.javaType = javaType;
    }

    public Class<?> javaType() {
        return javaType;
    }

    /** Resolves the DDL fragment for this type, filling in precision/scale for DECIMAL. */
    public String ddl(ColumnDefinition column) {
        if (this != DECIMAL) {
            return ddlTemplate;
        }
        int precision = column.precision() != null ? column.precision() : DEFAULT_PRECISION;
        int scale = column.scale() != null ? column.scale() : DEFAULT_SCALE;
        return ddlTemplate.formatted(precision, scale);
    }

    public static ColumnType fromYamlName(String name) {
        if (name == null) {
            throw new IllegalStateException("Column type is required");
        }
        String normalized = name.trim().toUpperCase();
        if ("NUMERIC".equals(normalized)) {
            return DECIMAL;
        }
        try {
            return ColumnType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown column type: " + name);
        }
    }
}
