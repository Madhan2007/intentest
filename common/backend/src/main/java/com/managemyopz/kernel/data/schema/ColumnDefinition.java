/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.Objects;

public record ColumnDefinition(
    String name,
    ColumnType type,
    boolean nullable,
    boolean unique,
    boolean primaryKey,
    Integer maxLength,
    Integer precision,
    Integer scale,
    String defaultExpr
) {
    public ColumnDefinition {
        Objects.requireNonNull(name, "column name must not be null");
        Objects.requireNonNull(type, "column type must not be null");
    }
}
