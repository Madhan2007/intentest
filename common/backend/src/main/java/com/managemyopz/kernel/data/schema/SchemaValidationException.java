/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.List;

/** Thrown by {@link GenericValidationEngine} when a generic entity payload fails
 *  schema-driven validation. Mapped by KernelExceptionHandler to the exact same
 *  {@code validation_failed} 400 envelope shape as Bean-Validation failures. */
public class SchemaValidationException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public SchemaValidationException(List<FieldError> fieldErrors) {
        super("Schema validation failed");
        this.fieldErrors = fieldErrors;
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String code, String message) {}
}
