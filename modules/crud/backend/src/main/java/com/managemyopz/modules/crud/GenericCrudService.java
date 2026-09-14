/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.crud;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Isolation;
import com.managemyopz.kernel.data.schema.ColumnDefinition;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.ForeignKeyValidator;
import com.managemyopz.kernel.data.schema.GenericValidationEngine;
import com.managemyopz.kernel.data.schema.SchemaConstants;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.schema.SchemaValidationException;
import com.managemyopz.kernel.data.schema.web.BulkEntityRequest;
import com.managemyopz.kernel.data.schema.web.BulkResult;
import com.managemyopz.kernel.data.schema.web.FilterSpec;
import com.managemyopz.kernel.data.schema.web.PageEnvelope;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates create/read/update/delete/filter/count/bulk for any entity known to
 * {@link SchemaRegistry}. No Spring stereotype — wired as a bean by
 * {@link CrudAutoConfiguration} so it only exists while the crud module is enabled.
 */
public class GenericCrudService {

    private final SchemaRegistry schemaRegistry;
    private final GenericCrudRepository repository;
    private final GenericValidationEngine validationEngine;
    private final ForeignKeyValidator foreignKeyValidator;
    private final DataClientRegistry dataClientRegistry;

    public GenericCrudService(SchemaRegistry schemaRegistry, GenericCrudRepository repository,
                               GenericValidationEngine validationEngine, ForeignKeyValidator foreignKeyValidator,
                               DataClientRegistry dataClientRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.repository = repository;
        this.validationEngine = validationEngine;
        this.foreignKeyValidator = foreignKeyValidator;
        this.dataClientRegistry = dataClientRegistry;
    }

    public Map<String, Object> create(String entity, Map<String, Object> row) {
        EntitySchema schema = schemaRegistry.require(entity);
        validate(schema, row, false);
        return repository.insert(schema, row);
    }

    public Map<String, Object> read(String entity, String id) {
        EntitySchema schema = schemaRegistry.require(entity);
        return repository.findById(schema, parseId(schema, id))
            .orElseThrow(() -> DataClientException.notFound(entity + " not found with id: " + id));
    }

    public PageEnvelope filter(String entity, FilterSpec filter) {
        EntitySchema schema = schemaRegistry.require(entity);
        int size = Math.max(SchemaConstants.MIN_PAGE_SIZE, Math.min(filter.size(), SchemaConstants.MAX_PAGE_SIZE));
        int page = Math.max(SchemaConstants.DEFAULT_PAGE, filter.page());
        int offset = page * size;

        List<Map<String, Object>> items = repository.findFiltered(schema, filter, size, offset);
        long totalItems = repository.count(schema, filter);
        int totalPages = (int) Math.ceil((double) totalItems / size);
        boolean hasMore = (long) (page + 1) * size < totalItems;

        return new PageEnvelope(items, page, size, totalItems, totalPages, hasMore);
    }

    public long count(String entity, FilterSpec filter) {
        EntitySchema schema = schemaRegistry.require(entity);
        return repository.count(schema, filter);
    }

    public Map<String, Object> update(String entity, String id, Map<String, Object> patch) {
        EntitySchema schema = schemaRegistry.require(entity);
        validate(schema, patch, true);
        Object pk = parseId(schema, id);
        return repository.update(schema, pk, patch)
            .orElseThrow(() -> DataClientException.notFound(entity + " not found with id: " + id));
    }

    public void delete(String entity, String id) {
        EntitySchema schema = schemaRegistry.require(entity);
        int affected = repository.delete(schema, parseId(schema, id));
        if (affected == 0) {
            throw DataClientException.notFound(entity + " not found with id: " + id);
        }
    }

    /** All-or-nothing: the whole batch runs in one transaction, on whichever
     *  connection the entity's schema resolves to (the existing
     *  {@code DataClient.transaction} port already rolls back on any exception),
     *  so a single failing row aborts the entire request with nothing committed. */
    public BulkResult bulk(String entity, BulkEntityRequest request) {
        EntitySchema schema = schemaRegistry.require(entity);
        String op = request.op() == null ? "" : request.op();
        return switch (op) {
            case "create" -> bulkCreate(schema, request.rows());
            case "update" -> bulkUpdate(schema, request.patches());
            case "delete" -> bulkDelete(schema, request.ids());
            default -> throw new IllegalArgumentException("Unsupported bulk op: " + request.op());
        };
    }

    private BulkResult bulkCreate(EntitySchema schema, List<Map<String, Object>> rows) {
        requireNonEmpty(rows, "rows");
        return dataClientRegistry.resolve(schema).transaction(Isolation.READ_COMMITTED, tx -> {
            for (Map<String, Object> row : rows) {
                validate(schema, row, false);
                repository.insert(schema, row);
            }
            return new BulkResult("create", rows.size());
        });
    }

    private BulkResult bulkUpdate(EntitySchema schema, List<BulkEntityRequest.PatchItem> patches) {
        requireNonEmpty(patches, "patches");
        return dataClientRegistry.resolve(schema).transaction(Isolation.READ_COMMITTED, tx -> {
            for (BulkEntityRequest.PatchItem item : patches) {
                validate(schema, item.patch(), true);
                Object pk = parseId(schema, item.id());
                repository.update(schema, pk, item.patch())
                    .orElseThrow(() -> DataClientException.notFound(schema.entity() + " not found with id: " + item.id()));
            }
            return new BulkResult("update", patches.size());
        });
    }

    private BulkResult bulkDelete(EntitySchema schema, List<String> ids) {
        requireNonEmpty(ids, "ids");
        return dataClientRegistry.resolve(schema).transaction(Isolation.READ_COMMITTED, tx -> {
            for (String id : ids) {
                int affected = repository.delete(schema, parseId(schema, id));
                if (affected == 0) {
                    throw DataClientException.notFound(schema.entity() + " not found with id: " + id);
                }
            }
            return new BulkResult("delete", ids.size());
        });
    }

    private void requireNonEmpty(List<?> values, String field) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("bulk." + field + " must not be empty");
        }
    }

    private void validate(EntitySchema schema, Map<String, Object> payload, boolean isPatch) {
        List<SchemaValidationException.FieldError> errors = validationEngine.validate(schema, payload, isPatch);
        if (errors.isEmpty()) {
            errors = foreignKeyValidator.validate(schema, payload, isPatch);
        }
        if (!errors.isEmpty()) {
            throw new SchemaValidationException(errors);
        }
    }

    private Object parseId(EntitySchema schema, String rawId) {
        ColumnDefinition pk = schema.primaryKeyColumn();
        try {
            return switch (pk.type()) {
                case UUID -> UUID.fromString(rawId.trim());
                case BIGINT -> Long.parseLong(rawId.trim());
                case INTEGER -> Integer.parseInt(rawId.trim());
                default -> rawId.trim();
            };
        } catch (IllegalArgumentException e) {
            throw new SchemaValidationException(List.of(new SchemaValidationException.FieldError(
                pk.name(), "type_mismatch", "id must be a valid " + pk.type() + " value")));
        }
    }
}
