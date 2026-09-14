/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DdlBuilderTest {

    private static final ColumnDefinition ID = new ColumnDefinition(
        "id", ColumnType.UUID, false, false, true, null, null, null, "gen_random_uuid()");
    private static final ColumnDefinition CODE = new ColumnDefinition(
        "code", ColumnType.TEXT, false, true, false, 32, null, null, null);
    private static final ColumnDefinition DESCRIPTION = new ColumnDefinition(
        "description", ColumnType.TEXT, true, false, false, 1000, null, null, null);

    private static final EntitySchema DEPT = new EntitySchema(
        "dept", "dept", 1,
        List.of(ID, CODE, DESCRIPTION),
        List.of(new IndexDefinition("idx_dept_code", List.of("code"), true, "asc")),
        List.of(new ForeignKeyDefinition("code", "region", "code",
            ForeignKeyDefinition.OnDelete.RESTRICT, ForeignKeyDefinition.Enforce.POSTGRES)),
        null, true
    );

    @Test
    @DisplayName("createTable emits CREATE TABLE IF NOT EXISTS with a primary key clause")
    void createTable() {
        String sql = DdlBuilder.createTable(DEPT);
        assertThat(sql)
            .startsWith("CREATE TABLE IF NOT EXISTS dept (")
            .contains("id UUID DEFAULT gen_random_uuid() NOT NULL")
            .contains("code TEXT NOT NULL UNIQUE")
            .contains("description TEXT")
            .contains("PRIMARY KEY (id)");
    }

    @Test
    @DisplayName("addColumn emits ADD COLUMN IF NOT EXISTS")
    void addColumn() {
        String sql = DdlBuilder.addColumn(DEPT, DESCRIPTION);
        assertThat(sql).isEqualTo("ALTER TABLE dept ADD COLUMN IF NOT EXISTS description TEXT");
    }

    @Test
    @DisplayName("createIndex emits CREATE UNIQUE INDEX IF NOT EXISTS")
    void createIndex() {
        String sql = DdlBuilder.createIndex(DEPT, DEPT.indexes().get(0));
        assertThat(sql).isEqualTo("CREATE UNIQUE INDEX IF NOT EXISTS idx_dept_code ON dept (code)");
    }

    @Test
    @DisplayName("createIndex applies DESC only for a single-column descending index")
    void createIndexDescending() {
        IndexDefinition desc = new IndexDefinition("idx_dept_created", List.of("code"), false, "desc");
        String sql = DdlBuilder.createIndex(DEPT, desc);
        assertThat(sql).isEqualTo("CREATE INDEX IF NOT EXISTS idx_dept_created ON dept (code DESC)");
    }

    @Test
    @DisplayName("addForeignKey emits ADD CONSTRAINT with a deterministic name")
    void addForeignKey() {
        ForeignKeyDefinition fk = DEPT.foreignKeys().get(0);
        String sql = DdlBuilder.addForeignKey(DEPT, fk);
        assertThat(sql).isEqualTo(
            "ALTER TABLE dept ADD CONSTRAINT fk_dept_code FOREIGN KEY (code) REFERENCES region(code) ON DELETE RESTRICT");
        assertThat(DdlBuilder.foreignKeyConstraintName(DEPT, fk)).isEqualTo("fk_dept_code");
    }

    @Test
    @DisplayName("no generated DDL ever contains a destructive keyword, across every builder method")
    void neverDestructive() {
        List<String> generated = List.of(
            DdlBuilder.createTable(DEPT),
            DdlBuilder.addColumn(DEPT, DESCRIPTION),
            DdlBuilder.createIndex(DEPT, DEPT.indexes().get(0)),
            DdlBuilder.addForeignKey(DEPT, DEPT.foreignKeys().get(0))
        );
        for (String sql : generated) {
            String upper = sql.toUpperCase();
            assertThat(upper).doesNotContain("DROP");
            assertThat(upper).doesNotContain("RENAME");
            assertThat(upper).doesNotContain("ALTER COLUMN");
            assertThat(upper).doesNotContain(" TYPE ");
        }
    }

    @Test
    @DisplayName("DECIMAL uses declared precision/scale, defaulting when absent")
    void decimalPrecisionScale() {
        ColumnDefinition amount = new ColumnDefinition(
            "amount", ColumnType.DECIMAL, false, false, false, null, 10, 2, null);
        ColumnDefinition amountDefaultPrecision = new ColumnDefinition(
            "amount", ColumnType.DECIMAL, false, false, false, null, null, null, null);

        assertThat(DdlBuilder.addColumn(DEPT, amount))
            .isEqualTo("ALTER TABLE dept ADD COLUMN IF NOT EXISTS amount NUMERIC(10,2) NOT NULL");
        assertThat(DdlBuilder.addColumn(DEPT, amountDefaultPrecision))
            .isEqualTo("ALTER TABLE dept ADD COLUMN IF NOT EXISTS amount NUMERIC(18,4) NOT NULL");
    }
}
