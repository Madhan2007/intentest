/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.stream.Collectors;

/**
 * Pure DDL text generation for the additive-only reconciliation policy: only
 * {@code CREATE TABLE IF NOT EXISTS}, {@code ADD COLUMN IF NOT EXISTS},
 * {@code CREATE INDEX IF NOT EXISTS}, and {@code ADD CONSTRAINT ... FOREIGN KEY}
 * are ever produced here. No method in this class can emit DROP, ALTER ... TYPE,
 * or RENAME — that is a structural guarantee, not a runtime check, so existing
 * data is never at risk from a schema definition change or removal.
 */
public final class DdlBuilder {

    private DdlBuilder() {
    }

    public static String createTable(EntitySchema schema) {
        SchemaIdentifierValidator.requireValidIdentifier(schema.table(), "table");
        String columnsSql = schema.columns().stream()
            .map(DdlBuilder::columnClause)
            .collect(Collectors.joining(", "));
        String pkClause = "PRIMARY KEY (" + schema.primaryKeyColumn().name() + ")";
        return "CREATE TABLE IF NOT EXISTS " + schema.table() + " (" + columnsSql + ", " + pkClause + ")";
    }

    public static String addColumn(EntitySchema schema, ColumnDefinition column) {
        SchemaIdentifierValidator.requireValidIdentifier(schema.table(), "table");
        return "ALTER TABLE " + schema.table() + " ADD COLUMN IF NOT EXISTS " + columnClause(column);
    }

    public static String createIndex(EntitySchema schema, IndexDefinition index) {
        SchemaIdentifierValidator.requireValidIdentifier(schema.table(), "table");
        SchemaIdentifierValidator.requireValidIdentifier(index.name(), "index");
        String columnList = index.columns().stream()
            .peek(c -> SchemaIdentifierValidator.requireValidIdentifier(c, "index column"))
            .collect(Collectors.joining(", "));
        String orderSuffix = index.columns().size() == 1 && "desc".equalsIgnoreCase(index.order()) ? " DESC" : "";
        String uniqueKeyword = index.unique() ? "UNIQUE " : "";
        return "CREATE " + uniqueKeyword + "INDEX IF NOT EXISTS " + index.name()
            + " ON " + schema.table() + " (" + columnList + orderSuffix + ")";
    }

    public static String addForeignKey(EntitySchema schema, ForeignKeyDefinition fk) {
        SchemaIdentifierValidator.requireValidIdentifier(schema.table(), "table");
        SchemaIdentifierValidator.requireValidIdentifier(fk.column(), "foreign key column");
        SchemaIdentifierValidator.requireValidIdentifier(fk.referencesTable(), "foreign key referenced table");
        SchemaIdentifierValidator.requireValidIdentifier(fk.referencesColumn(), "foreign key referenced column");
        String onDelete = switch (fk.onDelete()) {
            case CASCADE -> "CASCADE";
            case SET_NULL -> "SET NULL";
            case RESTRICT -> "RESTRICT";
        };
        return "ALTER TABLE " + schema.table() + " ADD CONSTRAINT " + foreignKeyConstraintName(schema, fk)
            + " FOREIGN KEY (" + fk.column() + ") REFERENCES " + fk.referencesTable() + "(" + fk.referencesColumn() + ")"
            + " ON DELETE " + onDelete;
    }

    /** Deterministic constraint name so the reconciler can detect an already-applied FK. */
    public static String foreignKeyConstraintName(EntitySchema schema, ForeignKeyDefinition fk) {
        return "fk_" + schema.table() + "_" + fk.column();
    }

    private static String columnClause(ColumnDefinition column) {
        SchemaIdentifierValidator.requireValidIdentifier(column.name(), "column");
        StringBuilder sql = new StringBuilder(column.name()).append(' ').append(column.type().ddl(column));
        if (column.defaultExpr() != null) {
            SchemaIdentifierValidator.requireValidDefaultExpression(column.defaultExpr());
            sql.append(" DEFAULT ").append(column.defaultExpr());
        }
        if (!column.nullable()) {
            sql.append(" NOT NULL");
        }
        if (column.unique() && !column.primaryKey()) {
            sql.append(" UNIQUE");
        }
        return sql.toString();
    }
}
