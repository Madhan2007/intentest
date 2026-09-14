/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class GenericValidationEngineTest {

    private static final ColumnDefinition ID = new ColumnDefinition(
        "id", ColumnType.UUID, false, false, true, null, null, null, "gen_random_uuid()");
    private static final ColumnDefinition CODE = new ColumnDefinition(
        "code", ColumnType.TEXT, false, true, false, 8, null, null, null);
    private static final ColumnDefinition DESCRIPTION = new ColumnDefinition(
        "description", ColumnType.TEXT, true, false, false, null, null, null, null);
    private static final ColumnDefinition CREATED_AT = new ColumnDefinition(
        "created_at", ColumnType.TIMESTAMPTZ, false, false, false, null, null, null, "now()");

    private static final EntitySchema DEPT = new EntitySchema(
        "dept", "dept", 1, List.of(ID, CODE, DESCRIPTION, CREATED_AT), List.of(), List.of(), null, true);

    private final GenericValidationEngine engine = new GenericValidationEngine();

    @Test
    @DisplayName("create: a required column with no default and not present fails as required")
    void createMissingRequiredField() {
        var errors = engine.validate(DEPT, Map.of(), false);
        assertThat(errors).extracting("field", "code").contains(tuple("code", "required"));
    }

    @Test
    @DisplayName("create: columns with a DEFAULT (id, created_at) are optional even though not nullable")
    void createAllowsOmittingColumnsWithDefaults() {
        var errors = engine.validate(DEPT, Map.of("code", "ENG"), false);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("create: an explicit null on a non-nullable column fails as required")
    void createExplicitNullOnRequiredField() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", null);
        var errors = engine.validate(DEPT, payload, false);
        assertThat(errors).extracting("field", "code").contains(tuple("code", "required"));
    }

    @Test
    @DisplayName("patch: a column simply absent from the patch is never required")
    void patchIgnoresAbsentFields() {
        var errors = engine.validate(DEPT, Map.of("description", "Updated"), true);
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("an unknown field is always rejected, in both create and patch mode")
    void rejectsUnknownField() {
        var errors = engine.validate(DEPT, Map.of("not_a_column", "x", "code", "ENG"), false);
        assertThat(errors).extracting("field", "code").contains(tuple("not_a_column", "unknown_field"));
    }

    @Test
    @DisplayName("a value that cannot parse as the column's type fails as type_mismatch")
    void rejectsTypeMismatch() {
        var errors = engine.validate(DEPT, Map.of("code", "ENG", "created_at", "not-a-timestamp"), false);
        assertThat(errors).extracting("field", "code").contains(tuple("created_at", "type_mismatch"));
    }

    @Test
    @DisplayName("a text value longer than max_length fails as max_length")
    void rejectsOverMaxLength() {
        var errors = engine.validate(DEPT, Map.of("code", "WAY-TOO-LONG-CODE"), false);
        assertThat(errors).extracting("field", "code").contains(tuple("code", "max_length"));
    }

    @Test
    @DisplayName("a well-formed UUID string is accepted for a uuid column")
    void acceptsUuidAsString() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "ENG");
        payload.put("id", UUID.randomUUID().toString());
        var errors = engine.validate(DEPT, payload, false);
        assertThat(errors).isEmpty();
    }
}
