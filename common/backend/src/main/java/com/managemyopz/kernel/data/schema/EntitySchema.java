/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Parsed, validated table schema — the runtime allowlist every generated
 *  statement (see {@link GenericSqlBuilder}, {@link DdlBuilder}) is checked against. */
public record EntitySchema(
    String entity,
    String table,
    int version,
    List<ColumnDefinition> columns,
    List<IndexDefinition> indexes,
    List<ForeignKeyDefinition> foreignKeys,
    /** Null = the default/primary DataClient connection. Non-null names a
     *  platform.yaml `databases:` entry, resolved via DataClientRegistry. */
    String database,
    /** False hides this entity from the generic /api/v1/opzhub/db/{entity}/*
     *  routes entirely (treated as unknown/not_found) — required for any entity
     *  whose data must never be reachable through generic CRUD, e.g. id_user. */
    boolean exposeGenericApi
) {
    public EntitySchema {
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
    }

    public Optional<ColumnDefinition> column(String name) {
        for (ColumnDefinition column : columns) {
            if (column.name().equals(name)) {
                return Optional.of(column);
            }
        }
        return Optional.empty();
    }

    public boolean hasColumn(String name) {
        return column(name).isPresent();
    }

    public ColumnDefinition primaryKeyColumn() {
        for (ColumnDefinition column : columns) {
            if (column.primaryKey()) {
                return column;
            }
        }
        throw new IllegalStateException("Entity " + entity + " has no primary key column");
    }

    public Set<String> columnNames() {
        return columns.stream().map(ColumnDefinition::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
