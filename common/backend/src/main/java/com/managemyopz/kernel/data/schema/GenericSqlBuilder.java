/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.schema.web.FilterSpec;
import com.managemyopz.kernel.data.schema.web.RangeSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure, stateless builder that turns a validated {@link EntitySchema} plus operation
 * input into parameterized SQL text. Every identifier (table/column names) comes
 * from the schema's own allowlist ({@link EntitySchema#columnNames()}); every value
 * is bound as a named parameter, never concatenated into the SQL string. Any payload
 * key not present in the schema is rejected before any SQL is built.
 */
public final class GenericSqlBuilder {

    public record SqlCommand(String sql, Map<String, Object> params) {}

    private GenericSqlBuilder() {
    }

    public static SqlCommand insert(EntitySchema schema, Map<String, Object> row) {
        requireKnownColumns(schema, row.keySet());
        List<String> columns = new ArrayList<>(row.keySet());
        String columnList = String.join(", ", columns);
        String valueList = columns.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
        String returning = joinedColumnNames(schema);
        String sql = "INSERT INTO " + schema.table() + " (" + columnList + ") VALUES (" + valueList + ")"
            + " RETURNING " + returning;
        return new SqlCommand(sql, new HashMap<>(row));
    }

    public static SqlCommand selectById(EntitySchema schema, Object id) {
        String pk = schema.primaryKeyColumn().name();
        String columns = joinedColumnNames(schema);
        String sql = "SELECT " + columns + " FROM " + schema.table() + " WHERE " + pk + " = :id";
        return new SqlCommand(sql, Map.of("id", id));
    }

    public static SqlCommand selectFiltered(EntitySchema schema, FilterSpec filter, int limit, int offset) {
        requireKnownColumns(schema, filterColumnNames(filter));
        WhereClause where = buildWhere(schema, filter);
        String pk = schema.primaryKeyColumn().name();
        String sortColumn = filter.sortBy() != null ? filter.sortBy() : pk;
        String sortDir = "desc".equalsIgnoreCase(filter.sortDir()) ? "DESC" : "ASC";
        String columns = joinedColumnNames(schema);

        Map<String, Object> params = new HashMap<>(where.params());
        params.put("__limit", limit);
        params.put("__offset", offset);

        String sql = "SELECT " + columns + " FROM " + schema.table()
            + where.sqlSuffix()
            + " ORDER BY " + sortColumn + " " + sortDir + ", " + pk + " ASC"
            + " LIMIT :__limit OFFSET :__offset";
        return new SqlCommand(sql, params);
    }

    public static SqlCommand count(EntitySchema schema, FilterSpec filter) {
        requireKnownColumns(schema, filterColumnNames(filter));
        WhereClause where = buildWhere(schema, filter);
        String sql = "SELECT COUNT(*) AS total_count FROM " + schema.table() + where.sqlSuffix();
        return new SqlCommand(sql, where.params());
    }

    public static SqlCommand update(EntitySchema schema, Object id, Map<String, Object> patch) {
        requireKnownColumns(schema, patch.keySet());
        String pk = schema.primaryKeyColumn().name();
        if (patch.containsKey(pk)) {
            throw new IllegalArgumentException("Primary key column cannot be updated: " + pk);
        }
        String setClause = patch.keySet().stream().map(c -> c + " = :" + c).collect(Collectors.joining(", "));
        String returning = joinedColumnNames(schema);
        String sql = "UPDATE " + schema.table() + " SET " + setClause + " WHERE " + pk + " = :id"
            + " RETURNING " + returning;

        Map<String, Object> params = new HashMap<>(patch);
        params.put("id", id);
        return new SqlCommand(sql, params);
    }

    public static SqlCommand delete(EntitySchema schema, Object id) {
        String pk = schema.primaryKeyColumn().name();
        String sql = "DELETE FROM " + schema.table() + " WHERE " + pk + " = :id";
        return new SqlCommand(sql, Map.of("id", id));
    }

    /**
     * Existence probe that stops after the first matching row. Prefer this over
     * {@link #count} when only presence matters (foreign-key checks).
     *
     * @param schema referenced table
     * @param column referenced column
     * @param value bound lookup value
     * @return parameterized {@code SELECT 1 ... LIMIT 1}
     */
    public static SqlCommand exists(EntitySchema schema, String column, Object value) {
        if (!schema.hasColumn(column)) {
            throw new IllegalArgumentException("Unknown field for entity " + schema.entity() + ": " + column);
        }
        String sql = "SELECT 1 FROM " + schema.table() + " WHERE " + column + " = :value LIMIT 1";
        return new SqlCommand(sql, Map.of("value", value));
    }

    private static String joinedColumnNames(EntitySchema schema) {
        return schema.columns().stream().map(ColumnDefinition::name).collect(Collectors.joining(", "));
    }

    private static void requireKnownColumns(EntitySchema schema, Collection<String> names) {
        for (String name : names) {
            if (!schema.hasColumn(name)) {
                throw new IllegalArgumentException("Unknown field for entity " + schema.entity() + ": " + name);
            }
        }
    }

    private static Set<String> filterColumnNames(FilterSpec filter) {
        Set<String> names = new LinkedHashSet<>();
        if (filter.eq() != null) {
            names.addAll(filter.eq().keySet());
        }
        if (filter.in() != null) {
            names.addAll(filter.in().keySet());
        }
        if (filter.range() != null) {
            names.addAll(filter.range().keySet());
        }
        if (filter.contains() != null) {
            names.addAll(filter.contains().keySet());
        }
        if (filter.sortBy() != null) {
            names.add(filter.sortBy());
        }
        return names;
    }

    private record WhereClause(String sqlSuffix, Map<String, Object> params) {}

    private static WhereClause buildWhere(EntitySchema schema, FilterSpec filter) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        int rangeParamCounter = 0;

        if (filter.eq() != null) {
            for (var entry : filter.eq().entrySet()) {
                String paramName = "eq_" + entry.getKey();
                clauses.add(entry.getKey() + " = :" + paramName);
                params.put(paramName, entry.getValue());
            }
        }
        if (filter.in() != null) {
            for (var entry : filter.in().entrySet()) {
                List<Object> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    // An empty IN-list can never match — short-circuit instead of
                    // sending an invalid `IN ()` clause to Postgres.
                    clauses.add("1 = 0");
                    continue;
                }
                String paramName = "in_" + entry.getKey();
                clauses.add(entry.getKey() + " IN (:" + paramName + ")");
                params.put(paramName, values);
            }
        }
        if (filter.range() != null) {
            for (var entry : filter.range().entrySet()) {
                rangeParamCounter = appendRangeClauses(entry.getKey(), entry.getValue(), clauses, params, rangeParamCounter);
            }
        }
        if (filter.contains() != null) {
            appendContainsClauses(schema, filter.contains(), clauses, params);
        }

        String suffix = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        return new WhereClause(suffix, params);
    }

    private static void appendContainsClauses(
            EntitySchema schema, Map<String, Object> contains, List<String> clauses, Map<String, Object> params) {
        for (var entry : contains.entrySet()) {
            ColumnDefinition column = schema.column(entry.getKey()).orElseThrow(() ->
                new IllegalArgumentException("Unknown field for entity " + schema.entity() + ": " + entry.getKey()));
            if (column.type() != ColumnType.JSONB) {
                throw new IllegalArgumentException("contains is only valid for jsonb columns: " + entry.getKey());
            }
            String paramName = "contains_" + entry.getKey();
            clauses.add(entry.getKey() + " @> jsonb_build_array(:" + paramName + ")");
            params.put(paramName, entry.getValue());
        }
    }

    private static int appendRangeClauses(
            String column, RangeSpec range, List<String> clauses, Map<String, Object> params, int counter) {
        counter = appendRangeClause(column, ">=", range.gte(), clauses, params, counter);
        counter = appendRangeClause(column, "<=", range.lte(), clauses, params, counter);
        counter = appendRangeClause(column, ">", range.gt(), clauses, params, counter);
        counter = appendRangeClause(column, "<", range.lt(), clauses, params, counter);
        return counter;
    }

    private static int appendRangeClause(
            String column, String operator, Object value, List<String> clauses, Map<String, Object> params, int counter) {
        if (value == null) {
            return counter;
        }
        String paramName = "range_" + counter;
        clauses.add(column + " " + operator + " :" + paramName);
        params.put(paramName, value);
        return counter + 1;
    }
}
