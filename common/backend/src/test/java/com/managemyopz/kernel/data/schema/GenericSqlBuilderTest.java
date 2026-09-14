/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.web.FilterSpec;
import com.managemyopz.kernel.data.schema.web.RangeSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericSqlBuilderTest {

    private static final ColumnDefinition ID = new ColumnDefinition(
        "id", ColumnType.UUID, false, false, true, null, null, null, "gen_random_uuid()");
    private static final ColumnDefinition CODE = new ColumnDefinition(
        "code", ColumnType.TEXT, false, true, false, 32, null, null, null);
    private static final ColumnDefinition NAME = new ColumnDefinition(
        "name", ColumnType.TEXT, false, false, false, 200, null, null, null);

    private static final EntitySchema DEPT = new EntitySchema(
        "dept", "dept", 1, List.of(ID, CODE, NAME), List.of(), List.of(), null, true);

    @Test
    @DisplayName("insert builds an INSERT ... RETURNING with only the supplied columns bound")
    void insert() {
        SqlCommand command = GenericSqlBuilder.insert(DEPT, Map.of("code", "ENG", "name", "Engineering"));
        assertThat(command.sql()).contains("INSERT INTO dept").contains("RETURNING id, code, name");
        assertThat(command.params()).containsOnly(Map.entry("code", "ENG"), Map.entry("name", "Engineering"));
    }

    @Test
    @DisplayName("insert rejects a payload key that is not a known column")
    void insertRejectsUnknownColumn() {
        assertThatThrownBy(() -> GenericSqlBuilder.insert(DEPT, Map.of("hacked_column", "x")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("selectById filters on the primary key column")
    void selectById() {
        UUID id = UUID.randomUUID();
        SqlCommand command = GenericSqlBuilder.selectById(DEPT, id);
        assertThat(command.sql()).isEqualTo("SELECT id, code, name FROM dept WHERE id = :id");
        assertThat(command.params()).containsEntry("id", id);
    }

    @Test
    @DisplayName("selectFiltered builds eq/in/range predicates and binds pagination")
    void selectFilteredBuildsAllPredicateKinds() {
        FilterSpec filter = new FilterSpec(
            Map.of("code", "ENG"),
            Map.of("name", List.of("Engineering", "Ops")),
            Map.of("code", new RangeSpec("A", "Z", null, null)),
            "name", "desc", 1, 10
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(DEPT, filter, 10, 10);

        assertThat(command.sql())
            .contains("code = :eq_code")
            .contains("name IN (:in_name)")
            .contains("code >= :range_0")
            .contains("code <= :range_1")
            .contains("ORDER BY name DESC, id ASC")
            .contains("LIMIT :__limit OFFSET :__offset");
        assertThat(command.params())
            .containsEntry("eq_code", "ENG")
            .containsEntry("in_name", List.of("Engineering", "Ops"))
            .containsEntry("range_0", "A")
            .containsEntry("range_1", "Z")
            .containsEntry("__limit", 10)
            .containsEntry("__offset", 10);
    }

    @Test
    @DisplayName("an empty IN list short-circuits to no results instead of invalid SQL")
    void selectFilteredEmptyInList() {
        FilterSpec filter = new FilterSpec(null, Map.of("code", List.of()), null, null, null, 0, 20);
        SqlCommand command = GenericSqlBuilder.selectFiltered(DEPT, filter, 20, 0);
        assertThat(command.sql()).contains("WHERE 1 = 0");
    }

    @Test
    @DisplayName("selectFiltered rejects a filter/sort column that is not a known column")
    void selectFilteredRejectsUnknownColumn() {
        FilterSpec filter = new FilterSpec(Map.of("hacked; DROP TABLE dept;--", "x"), null, null, null, null, 0, 20);
        assertThatThrownBy(() -> GenericSqlBuilder.selectFiltered(DEPT, filter, 20, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("count reuses the same WHERE builder as selectFiltered")
    void count() {
        FilterSpec filter = new FilterSpec(Map.of("code", "ENG"), null, null, null, null, 0, 20);
        SqlCommand command = GenericSqlBuilder.count(DEPT, filter);
        assertThat(command.sql()).isEqualTo("SELECT COUNT(*) AS total_count FROM dept WHERE code = :eq_code");
        assertThat(command.params()).containsEntry("eq_code", "ENG");
    }

    @Test
    @DisplayName("update builds a partial-patch SET clause and rejects updating the primary key")
    void update() {
        UUID id = UUID.randomUUID();
        SqlCommand command = GenericSqlBuilder.update(DEPT, id, Map.of("name", "Engineering Core"));
        assertThat(command.sql()).isEqualTo("UPDATE dept SET name = :name WHERE id = :id RETURNING id, code, name");
        assertThat(command.params()).containsEntry("name", "Engineering Core").containsEntry("id", id);

        assertThatThrownBy(() -> GenericSqlBuilder.update(DEPT, id, Map.of("id", UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("exists stops after the first matching row")
    void exists() {
        UUID id = UUID.randomUUID();
        SqlCommand command = GenericSqlBuilder.exists(DEPT, "id", id);
        assertThat(command.sql()).isEqualTo("SELECT 1 FROM dept WHERE id = :value LIMIT 1");
        assertThat(command.params()).containsEntry("value", id);
    }

    @Test
    @DisplayName("contains builds a jsonb array-containment predicate")
    void containsJsonbArray() {
        ColumnDefinition references = new ColumnDefinition(
            "company_references", ColumnType.JSONB, true, false, false, null, null, null, null);
        EntitySchema company = new EntitySchema(
            "company_information", "company_information", 1,
            List.of(ID, references), List.of(), List.of(), "OPZMAIN", true);
        FilterSpec filter = new FilterSpec(
            null, null, null, null, null, 0, 2, Map.of("company_references", "technosprint"));
        SqlCommand command = GenericSqlBuilder.selectFiltered(company, filter, 2, 0);
        assertThat(command.sql()).contains(
            "company_references @> jsonb_build_array(:contains_company_references)");
        assertThat(command.params()).containsEntry("contains_company_references", "technosprint");
    }

    @Test
    @DisplayName("contains rejects a non-jsonb column")
    void containsRejectsNonJsonb() {
        FilterSpec filter = new FilterSpec(
            null, null, null, null, null, 0, 20, Map.of("code", "ENG"));
        assertThatThrownBy(() -> GenericSqlBuilder.selectFiltered(DEPT, filter, 20, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jsonb");
    }

    @Test
    @DisplayName("delete filters on the primary key column")
    void delete() {
        UUID id = UUID.randomUUID();
        SqlCommand command = GenericSqlBuilder.delete(DEPT, id);
        assertThat(command.sql()).isEqualTo("DELETE FROM dept WHERE id = :id");
        assertThat(command.params()).containsEntry("id", id);
    }
}
