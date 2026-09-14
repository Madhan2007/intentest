/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

public record FilterEntityRequest(FilterSpec filter) {
    public FilterEntityRequest {
        if (filter == null) {
            filter = new FilterSpec(null, null, null, null, null, null, null);
        }
    }
}
