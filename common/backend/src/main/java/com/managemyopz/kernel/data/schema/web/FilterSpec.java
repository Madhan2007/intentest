/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

import com.managemyopz.kernel.data.schema.SchemaConstants;

import java.util.List;
import java.util.Map;

/**
 * Generic, entity-agnostic filter contract reused across every table's
 * {@code /filter} and {@code /count} operations: equality, membership, range,
 * and jsonb array containment, plus sort and pagination.
 */
public record FilterSpec(
    Map<String, Object> eq,
    Map<String, List<Object>> in,
    Map<String, RangeSpec> range,
    String sortBy,
    String sortDir,
    Integer page,
    Integer size,
    Map<String, Object> contains
) {
    public FilterSpec {
        if (page == null) {
            page = SchemaConstants.DEFAULT_PAGE;
        }
        if (size == null) {
            size = SchemaConstants.DEFAULT_PAGE_SIZE;
        }
    }

    /**
     * Compatibility constructor for callers that do not use jsonb containment.
     *
     * @param eq equality predicates
     * @param in membership predicates
     * @param range range predicates
     * @param sortBy sort column
     * @param sortDir sort direction
     * @param page page index
     * @param size page size
     */
    public FilterSpec(
            Map<String, Object> eq,
            Map<String, List<Object>> in,
            Map<String, RangeSpec> range,
            String sortBy,
            String sortDir,
            Integer page,
            Integer size) {
        this(eq, in, range, sortBy, sortDir, page, size, null);
    }
}
