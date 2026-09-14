/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaIdentifierValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"dept", "dept_code", "a", "widget2"})
    @DisplayName("accepts lower-snake identifiers")
    void acceptsValidIdentifiers(String value) {
        assertThatCode(() -> SchemaIdentifierValidator.requireValidIdentifier(value, "column"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Dept",                         // uppercase
        "1dept",                        // leading digit
        "dept-code",                    // hyphen
        "dept; DROP TABLE users;--",    // injection attempt
        "dept code",                    // space
        "dept.code",                    // dot
    })
    @DisplayName("rejects any identifier that is not lower-snake and Postgres-safe")
    void rejectsInvalidIdentifiers(String value) {
        assertThatThrownBy(() -> SchemaIdentifierValidator.requireValidIdentifier(value, "column"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects an identifier over 63 bytes (Postgres NAMEDATALEN)")
    void rejectsOverlongIdentifier() {
        String tooLong = "a" + "b".repeat(63);
        assertThatThrownBy(() -> SchemaIdentifierValidator.requireValidIdentifier(tooLong, "column"))
            .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"now()", "gen_random_uuid()", "true", "false", "0", "-1", "'ACTIVE'", "'{}'"})
    @DisplayName("accepts the allowlisted default expression tokens")
    void acceptsAllowlistedDefaults(String expr) {
        assertThatCode(() -> SchemaIdentifierValidator.requireValidDefaultExpression(expr))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "now() + interval '1 day'",     // arbitrary expression
        "(SELECT 1)",                   // subquery
        "'; DROP TABLE dept; --",       // injection attempt
        "gen_random_uuid",              // missing parens
    })
    @DisplayName("rejects any default expression outside the allowlist")
    void rejectsDisallowedDefaults(String expr) {
        assertThatThrownBy(() -> SchemaIdentifierValidator.requireValidDefaultExpression(expr))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a null default expression is always allowed (column has no default)")
    void allowsNullDefault() {
        assertThatCode(() -> SchemaIdentifierValidator.requireValidDefaultExpression(null))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPZUSER", "OPZMAIN", "OPZHUB", "A", "MY_DB_1"})
    @DisplayName("accepts uppercase-snake database keys")
    void acceptsValidDatabaseKeys(String value) {
        assertThatCode(() -> SchemaIdentifierValidator.requireValidDatabaseKey(value))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"opzuser", "OpzUser", "1OPZ", "OPZ-USER", "OPZ USER"})
    @DisplayName("rejects a database key that is not uppercase-snake")
    void rejectsInvalidDatabaseKeys(String value) {
        assertThatThrownBy(() -> SchemaIdentifierValidator.requireValidDatabaseKey(value))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a null database key")
    void rejectsNullDatabaseKey() {
        assertThatThrownBy(() -> SchemaIdentifierValidator.requireValidDatabaseKey(null))
            .isInstanceOf(IllegalStateException.class);
    }
}
