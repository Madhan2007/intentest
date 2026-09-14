/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

import java.util.List;
import java.util.Map;

/** {@code op} is one of "create" | "update" | "delete"; only the matching list
 *  ({@code rows} | {@code patches} | {@code ids}) needs to be populated. The whole
 *  batch runs in one transaction — any row failing rolls back the entire request. */
public record BulkEntityRequest(
    String op,
    List<Map<String, Object>> rows,
    List<PatchItem> patches,
    List<String> ids
) {
    public record PatchItem(String id, Map<String, Object> patch) {}
}
