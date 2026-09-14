/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

import java.util.List;
import java.util.Map;

public record PageEnvelope(
    List<Map<String, Object>> items,
    int page,
    int size,
    long totalItems,
    int totalPages,
    boolean hasMore
) {}
