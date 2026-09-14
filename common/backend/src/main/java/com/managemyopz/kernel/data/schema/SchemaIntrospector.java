/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads the live Postgres shape of a table via read-only {@code information_schema}
 * / {@code pg_indexes} system views (never user data) so {@link SchemaReconciler} can
 * diff it against the declared {@link EntitySchema}. The table name is always bound
 * as a query parameter, never concatenated into the SQL text.
 */
@Component
public class SchemaIntrospector {

    private static final String SCHEMA_NAME = "public";

    public record LiveTable(boolean exists, Set<String> columns, Set<String> indexes, Set<String> foreignKeys) {}

    /**
     * Introspects a table's live shape on the given target connection. Stateless
     * on purpose — different entities may be reconciled against different
     * physical databases (see DataClientRegistry), so the target is passed per call
     * rather than fixed at construction.
     */
    public LiveTable introspect(DataClient dataClient, String table) {
        Map<String, Object> params = Map.of("table_name", table, "schema_name", SCHEMA_NAME);

        boolean exists = !dataClient.queryRaw(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = :schema_name AND table_name = :table_name",
            params
        ).isEmpty();

        if (!exists) {
            return new LiveTable(false, Set.of(), Set.of(), Set.of());
        }

        Set<String> columns = new LinkedHashSet<>();
        for (Row row : dataClient.queryRaw(
                "SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = :schema_name AND table_name = :table_name",
                params)) {
            columns.add(row.getString("column_name"));
        }

        Set<String> indexes = new LinkedHashSet<>();
        for (Row row : dataClient.queryRaw(
                "SELECT indexname FROM pg_indexes WHERE schemaname = :schema_name AND tablename = :table_name",
                params)) {
            indexes.add(row.getString("indexname"));
        }

        Set<String> foreignKeys = new LinkedHashSet<>();
        for (Row row : dataClient.queryRaw(
                "SELECT constraint_name FROM information_schema.table_constraints "
                    + "WHERE table_schema = :schema_name AND table_name = :table_name AND constraint_type = 'FOREIGN KEY'",
                params)) {
            foreignKeys.add(row.getString("constraint_name"));
        }

        return new LiveTable(true, columns, indexes, foreignKeys);
    }
}
