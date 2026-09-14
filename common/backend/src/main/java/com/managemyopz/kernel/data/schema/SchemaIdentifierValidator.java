/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.regex.Pattern;

/** Allowlist gate every generated identifier and DEFAULT expression must pass before
 *  being spliced into DDL/DML text. Re-checked at DDL-build time even though
 *  {@link SchemaRegistry} already validates at load — DdlBuilder must be safe to
 *  unit-test standalone without trusting caller order. */
public final class SchemaIdentifierValidator {

    private static final Pattern IDENTIFIER = Pattern.compile(SchemaConstants.IDENTIFIER_PATTERN);
    private static final Pattern DEFAULT_EXPR = Pattern.compile(SchemaConstants.DEFAULT_EXPR_PATTERN);
    private static final Pattern DATABASE_KEY = Pattern.compile(SchemaConstants.DATABASE_KEY_PATTERN);

    private SchemaIdentifierValidator() {
    }

    public static void requireValidIdentifier(String value, String kind) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException("Invalid " + kind + " identifier: " + value);
        }
    }

    public static void requireValidDefaultExpression(String expr) {
        if (expr != null && !DEFAULT_EXPR.matcher(expr).matches()) {
            throw new IllegalStateException("Disallowed default expression: " + expr);
        }
    }

    public static void requireValidDatabaseKey(String value) {
        if (value == null || !DATABASE_KEY.matcher(value).matches()) {
            throw new IllegalStateException("Invalid database key: " + value);
        }
    }
}
