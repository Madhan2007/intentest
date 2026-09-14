/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

/** Bulk writes are all-or-nothing (one transaction) — a result only ever
 *  represents full success; any failure throws instead and rolls back. */
public record BulkResult(String op, int count) {}
