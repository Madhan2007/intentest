/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema.web;

/** Inclusive/exclusive range bounds for one filtered column. Any subset may be null. */
public record RangeSpec(Object gte, Object lte, Object gt, Object lt) {}
