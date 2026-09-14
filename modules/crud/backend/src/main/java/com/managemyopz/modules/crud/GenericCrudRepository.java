/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.crud;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.web.FilterSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to Postgres for any entity via {@link GenericSqlBuilder}-built statements
 * and {@link DataClient#executeRaw}/{@link DataClient#queryRaw}, routed per-entity
 * through {@link DataClientRegistry} (an entity with no declared {@code database}
 * uses the default connection; one that declares e.g. {@code OPZMAIN} uses that
 * connection instead). No Spring stereotype — wired as a bean by
 * {@link CrudAutoConfiguration} so it only exists while the crud module is enabled.
 */
public class GenericCrudRepository {

    private final DataClientRegistry dataClientRegistry;

    public GenericCrudRepository(DataClientRegistry dataClientRegistry) {
        this.dataClientRegistry = dataClientRegistry;
    }

    public Map<String, Object> insert(EntitySchema schema, Map<String, Object> row) {
        SqlCommand command = GenericSqlBuilder.insert(schema, row);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params()).stream()
            .findFirst()
            .map(this::toMap)
            .orElseThrow(() -> new IllegalStateException("Insert returned no row"));
    }

    public Optional<Map<String, Object>> findById(EntitySchema schema, Object id) {
        SqlCommand command = GenericSqlBuilder.selectById(schema, id);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params()).stream()
            .findFirst().map(this::toMap);
    }

    public List<Map<String, Object>> findFiltered(EntitySchema schema, FilterSpec filter, int limit, int offset) {
        SqlCommand command = GenericSqlBuilder.selectFiltered(schema, filter, limit, offset);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params()).stream()
            .map(this::toMap).toList();
    }

    public long count(EntitySchema schema, FilterSpec filter) {
        SqlCommand command = GenericSqlBuilder.count(schema, filter);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params()).stream()
            .findFirst()
            .map(row -> row.getLong("total_count"))
            .orElse(0L);
    }

    public Optional<Map<String, Object>> update(EntitySchema schema, Object id, Map<String, Object> patch) {
        SqlCommand command = GenericSqlBuilder.update(schema, id, patch);
        return dataClientRegistry.resolve(schema).queryRaw(command.sql(), command.params()).stream()
            .findFirst().map(this::toMap);
    }

    public int delete(EntitySchema schema, Object id) {
        SqlCommand command = GenericSqlBuilder.delete(schema, id);
        return dataClientRegistry.resolve(schema).executeRaw(command.sql(), command.params());
    }

    private Map<String, Object> toMap(Row row) {
        return new LinkedHashMap<>(row.values());
    }
}
