/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.module.ModuleCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Exercises the parsing/validation logic directly (package-private methods) rather
 *  than through classpath scanning, so a bad fixture can't collide with the real
 *  modules/crud/db/schema/dept.yaml already on the test classpath. */
class SchemaRegistryTest {

    private final SchemaRegistry registry = new SchemaRegistry(mock(ModuleCatalog.class));

    @Test
    @DisplayName("parses a well-formed schema document into an EntitySchema")
    void parsesWellFormedSchema() {
        Map<String, Object> doc = Map.of(
            "entity", "widget",
            "columns", List.of(
                Map.of("name", "id", "type", "uuid", "primary_key", true, "default", "gen_random_uuid()"),
                Map.of("name", "label", "type", "text", "max_length", 64)
            ),
            "indexes", List.of(Map.of("name", "idx_widget_label", "columns", List.of("label"))),
            "foreign_keys", List.of()
        );

        EntitySchema schema = registry.parse(doc, "widget.yaml");

        assertThat(schema.entity()).isEqualTo("widget");
        assertThat(schema.table()).isEqualTo("widget");
        assertThat(schema.columnNames()).containsExactly("id", "label");
        assertThat(schema.primaryKeyColumn().name()).isEqualTo("id");
        assertThat(schema.indexes()).hasSize(1);
        assertThat(schema.database()).isNull();
        assertThat(schema.exposeGenericApi()).isTrue();
    }

    @Test
    @DisplayName("a declared database routes to that named connection; exposeGenericApi can be turned off")
    void parsesDatabaseAndExposeFlag() {
        Map<String, Object> doc = Map.of(
            "entity", "id_user",
            "database", "OPZUSER",
            "expose_generic_api", false,
            "columns", List.of(Map.of("name", "id", "type", "uuid", "primary_key", true))
        );
        EntitySchema schema = registry.parse(doc, "id_user.yaml");
        assertThat(schema.database()).isEqualTo("OPZUSER");
        assertThat(schema.exposeGenericApi()).isFalse();
    }

    @Test
    @DisplayName("rejects a database key that isn't uppercase-snake")
    void rejectsInvalidDatabaseKey() {
        Map<String, Object> doc = Map.of(
            "entity", "widget",
            "database", "opzuser",
            "columns", List.of(Map.of("name", "id", "type", "uuid", "primary_key", true))
        );
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a table name distinct from entity is honored")
    void honorsExplicitTableName() {
        Map<String, Object> doc = Map.of(
            "entity", "hr_dept",
            "table", "dept",
            "columns", List.of(Map.of("name", "id", "type", "uuid", "primary_key", true))
        );
        EntitySchema schema = registry.parse(doc, "hr_dept.yaml");
        assertThat(schema.entity()).isEqualTo("hr_dept");
        assertThat(schema.table()).isEqualTo("dept");
    }

    @Test
    @DisplayName("rejects a schema with no columns")
    void rejectsNoColumns() {
        Map<String, Object> doc = Map.of("entity", "widget", "columns", List.of());
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a schema with no primary_key column")
    void rejectsNoPrimaryKey() {
        Map<String, Object> doc = Map.of(
            "entity", "widget",
            "columns", List.of(Map.of("name", "label", "type", "text"))
        );
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects an entity name that is not a safe identifier")
    void rejectsInvalidEntityName() {
        Map<String, Object> doc = Map.of(
            "entity", "widget; DROP TABLE dept;--",
            "columns", List.of(Map.of("name", "id", "type", "uuid", "primary_key", true))
        );
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects a column default expression outside the allowlist")
    void rejectsDisallowedDefault() {
        Map<String, Object> doc = Map.of(
            "entity", "widget",
            "columns", List.of(Map.of(
                "name", "id", "type", "uuid", "primary_key", true, "default", "(SELECT 1)"))
        );
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rejects an unknown column type")
    void rejectsUnknownColumnType() {
        Map<String, Object> doc = Map.of(
            "entity", "widget",
            "columns", List.of(Map.of("name", "id", "type", "money", "primary_key", true))
        );
        assertThatThrownBy(() -> registry.parse(doc, "widget.yaml")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("require() treats expose_generic_api=false the same as an unknown entity")
    void requireHidesEntitiesNotExposedGenerically() throws Exception {
        EntitySchema hidden = new EntitySchema(
            "id_user", "id_user", 1,
            List.of(new ColumnDefinition("id", ColumnType.UUID, false, false, true, null, null, null, null)),
            List.of(), List.of(), "OPZUSER", false);

        @SuppressWarnings("unchecked")
        Map<String, EntitySchema> entities = (Map<String, EntitySchema>)
            getPrivateField(registry, "entities");
        entities.put(hidden.entity(), hidden);

        assertThat(registry.find("id_user")).isPresent();
        assertThatThrownBy(() -> registry.require("id_user"))
            .isInstanceOf(DataClientException.class)
            .hasMessageContaining("id_user");
    }

    private static Object getPrivateField(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    @DisplayName("foreign key defaults to on_delete=RESTRICT and enforce=POSTGRES when omitted")
    void foreignKeyDefaults() {
        ForeignKeyDefinition fk = registry.parseForeignKey(Map.of(
            "column", "region_id", "references_table", "region", "references_column", "id"));
        assertThat(fk.onDelete()).isEqualTo(ForeignKeyDefinition.OnDelete.RESTRICT);
        assertThat(fk.enforce()).isEqualTo(ForeignKeyDefinition.Enforce.POSTGRES);
        assertThat(fk.effectiveEnforce("OPZMAIN", "OPZMAIN")).isEqualTo(ForeignKeyDefinition.Enforce.POSTGRES);
        assertThat(fk.effectiveEnforce("OPZUSER", "OPZMAIN")).isEqualTo(ForeignKeyDefinition.Enforce.APP);
        assertThat(fk.effectiveEnforce(null, null)).isEqualTo(ForeignKeyDefinition.Enforce.POSTGRES);
    }

    @Test
    @DisplayName("findByTable matches a loaded schema by physical table name")
    void findByTable() throws Exception {
        EntitySchema user = new EntitySchema(
            "id_user", "id_user", 1,
            List.of(new ColumnDefinition("id", ColumnType.UUID, false, false, true, null, null, null, null)),
            List.of(), List.of(), "OPZUSER", false);

        @SuppressWarnings("unchecked")
        Map<String, EntitySchema> entities = (Map<String, EntitySchema>)
            getPrivateField(registry, "entities");
        entities.put(user.entity(), user);

        assertThat(registry.findByTable("id_user")).contains(user);
        assertThat(registry.findByTable("missing")).isEmpty();
    }
}
