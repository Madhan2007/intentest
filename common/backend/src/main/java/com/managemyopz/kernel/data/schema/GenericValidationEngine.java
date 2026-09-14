/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Required/nullable/type/max-length checks driven entirely by {@link EntitySchema},
 * replacing per-entity Bean Validation annotations now that payloads are generic
 * field maps rather than typed DTOs.
 */
@Component
public class GenericValidationEngine {

    public List<SchemaValidationException.FieldError> validate(
            EntitySchema schema, Map<String, Object> payload, boolean isPatch) {
        List<SchemaValidationException.FieldError> errors = new ArrayList<>();
        for (String field : payload.keySet()) {
            if (!schema.hasColumn(field)) {
                errors.add(new SchemaValidationException.FieldError(
                    field, "unknown_field", "Unknown field: " + field));
            }
        }

        for (ColumnDefinition column : schema.columns()) {
            validateColumn(column, payload, isPatch, errors);
        }
        return errors;
    }

    private void validateColumn(
            ColumnDefinition column, Map<String, Object> payload, boolean isPatch,
            List<SchemaValidationException.FieldError> errors) {
        boolean present = payload.containsKey(column.name());

        if (!present) {
            if (!isPatch && !column.nullable() && column.defaultExpr() == null) {
                errors.add(requiredError(column));
            }
            return;
        }

        Object value = payload.get(column.name());
        if (value == null) {
            if (!column.nullable()) {
                errors.add(requiredError(column));
            }
            return;
        }

        if (!typeMatches(column.type(), value)) {
            errors.add(new SchemaValidationException.FieldError(column.name(), "type_mismatch",
                "Expected a valid " + column.type() + " value for field: " + column.name()));
            return;
        }

        if (column.type() == ColumnType.TEXT && column.maxLength() != null
                && value.toString().length() > column.maxLength()) {
            errors.add(new SchemaValidationException.FieldError(column.name(), "max_length",
                "Field " + column.name() + " must not exceed " + column.maxLength() + " characters"));
        }
    }

    private SchemaValidationException.FieldError requiredError(ColumnDefinition column) {
        return new SchemaValidationException.FieldError(column.name(), "required", "Field is required: " + column.name());
    }

    private boolean typeMatches(ColumnType type, Object value) {
        return switch (type) {
            case UUID -> value instanceof UUID || parses(() -> UUID.fromString(value.toString()));
            case TEXT, JSONB -> value instanceof String;
            case INTEGER -> value instanceof Integer || parses(() -> Integer.parseInt(value.toString()));
            case BIGINT -> value instanceof Long || value instanceof Integer
                || parses(() -> Long.parseLong(value.toString()));
            case DECIMAL -> value instanceof Number || parses(() -> new BigDecimal(value.toString()));
            case BOOLEAN -> value instanceof Boolean;
            case TIMESTAMPTZ -> value instanceof Instant || parses(() -> Instant.parse(value.toString()));
            case DATE -> value instanceof LocalDate || parses(() -> LocalDate.parse(value.toString()));
            case TEXT_ARRAY -> value instanceof List<?> || value instanceof String[];
        };
    }

    private boolean parses(Runnable attempt) {
        try {
            attempt.run();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
