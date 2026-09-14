/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaReconcilerTest {

    private static final ColumnDefinition ID = new ColumnDefinition(
        "id", ColumnType.UUID, false, false, true, null, null, null, "gen_random_uuid()");
    private static final ColumnDefinition CODE = new ColumnDefinition(
        "code", ColumnType.TEXT, false, true, false, 32, null, null, null);
    private static final ColumnDefinition DESCRIPTION = new ColumnDefinition(
        "description", ColumnType.TEXT, true, false, false, null, null, null, null);

    private static final EntitySchema DEPT = new EntitySchema(
        "dept", "dept", 1,
        List.of(ID, CODE, DESCRIPTION),
        List.of(new IndexDefinition("idx_dept_code", List.of("code"), true, "asc")),
        List.of(),
        null, true
    );

    @Mock private SchemaRegistry schemaRegistry;
    @Mock private SchemaIntrospector introspector;
    @Mock private DataClientRegistry dataClientRegistry;
    @Mock private DataClient dataClient;
    @Mock private PlatformProperties platformProperties;
    @Mock private PlatformProperties.Db db;

    private SchemaReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new SchemaReconciler(schemaRegistry, introspector, dataClientRegistry, platformProperties);
    }

    @Test
    @DisplayName("skips entirely when db.type is not postgres")
    void skipsOnNonPostgres() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("memory");

        reconciler.run(null);

        verifyNoInteractions(schemaRegistry, introspector, dataClientRegistry);
    }

    @Test
    @DisplayName("creates the table plus its indexes when the table does not exist yet")
    void createsMissingTable() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(DEPT));
        when(dataClientRegistry.resolve(DEPT)).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "dept")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));

        reconciler.run(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataClient, org.mockito.Mockito.times(2)).executeRaw(sqlCaptor.capture(), anyMap());
        List<String> executed = sqlCaptor.getAllValues();
        assertThat(executed.get(0)).startsWith("CREATE TABLE IF NOT EXISTS dept");
        assertThat(executed.get(1)).startsWith("CREATE UNIQUE INDEX IF NOT EXISTS idx_dept_code");
        assertThat(executed).noneMatch(sql -> sql.toUpperCase().contains("DROP"));
    }

    @Test
    @DisplayName("adds only the missing column when the table already exists")
    void addsOnlyMissingColumn() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(DEPT));
        when(dataClientRegistry.resolve(DEPT)).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "dept")).thenReturn(new SchemaIntrospector.LiveTable(
            true, Set.of("id", "code"), Set.of("idx_dept_code"), Set.of()));

        reconciler.run(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataClient, org.mockito.Mockito.times(1)).executeRaw(sqlCaptor.capture(), anyMap());
        assertThat(sqlCaptor.getValue()).isEqualTo("ALTER TABLE dept ADD COLUMN IF NOT EXISTS description TEXT");
    }

    @Test
    @DisplayName("does nothing when the live table already matches the declared schema")
    void noOpWhenAlreadyMatching() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(DEPT));
        when(dataClientRegistry.resolve(DEPT)).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "dept")).thenReturn(new SchemaIntrospector.LiveTable(
            true, Set.of("id", "code", "description"), Set.of("idx_dept_code"), Set.of()));

        reconciler.run(null);

        verify(dataClient, never()).executeRaw(anyString(), anyMap());
    }

    @Test
    @DisplayName("one entity's reconciliation failure does not stop the others")
    void continuesAfterOneFailure() {
        EntitySchema other = new EntitySchema(
            "region", "region", 1, List.of(ID, CODE), List.of(), List.of(), null, true);

        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(DEPT, other));
        when(dataClientRegistry.resolve(any(EntitySchema.class))).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "dept")).thenThrow(new RuntimeException("boom"));
        when(introspector.introspect(dataClient, "region")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));

        reconciler.run(null);

        verify(dataClient).executeRaw("CREATE TABLE IF NOT EXISTS region (id UUID DEFAULT gen_random_uuid() NOT NULL, "
            + "code TEXT NOT NULL UNIQUE, PRIMARY KEY (id))", Map.of());
    }

    @Test
    @DisplayName("two-pass reconciliation: every table/index is created before any foreign key is attempted, "
        + "regardless of the order entities are discovered in")
    void createsAllTablesBeforeAnyForeignKey() {
        EntitySchema parentA = new EntitySchema(
            "company_license", "company_license", 1, List.of(ID), List.of(), List.of(), "OPZMAIN", true);
        EntitySchema parentB = new EntitySchema(
            "server_details", "server_details", 1, List.of(ID), List.of(), List.of(), "OPZMAIN", true);
        EntitySchema child = new EntitySchema(
            "company_information", "company_information", 1, List.of(ID, CODE), List.of(),
            List.of(
                new ForeignKeyDefinition("code", "company_license", "id",
                    ForeignKeyDefinition.OnDelete.RESTRICT, ForeignKeyDefinition.Enforce.POSTGRES),
                new ForeignKeyDefinition("code", "server_details", "id",
                    ForeignKeyDefinition.OnDelete.RESTRICT, ForeignKeyDefinition.Enforce.POSTGRES)
            ),
            "OPZMAIN", true);

        // Deliberately FK-hostile order: the child (whose FKs reference the other two) comes first.
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(child, parentA, parentB));
        when(dataClientRegistry.resolve(any(EntitySchema.class))).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "company_information")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));
        when(introspector.introspect(dataClient, "company_license")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));
        when(introspector.introspect(dataClient, "server_details")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));

        reconciler.run(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataClient, org.mockito.Mockito.atLeastOnce()).executeRaw(sqlCaptor.capture(), anyMap());
        List<String> executed = sqlCaptor.getAllValues();

        int lastCreateTableIndex = -1;
        int firstAddConstraintIndex = -1;
        for (int i = 0; i < executed.size(); i++) {
            String sql = executed.get(i);
            if (sql.startsWith("CREATE TABLE")) {
                lastCreateTableIndex = i;
            }
            if (firstAddConstraintIndex == -1 && sql.contains("ADD CONSTRAINT")) {
                firstAddConstraintIndex = i;
            }
        }
        assertThat(lastCreateTableIndex).isLessThan(firstAddConstraintIndex);
    }

    @Test
    @DisplayName("does not emit a Postgres foreign key when the referenced table lives on another database")
    void skipsCrossDatabaseForeignKey() {
        EntitySchema parent = new EntitySchema(
            "company_information", "company_information", 1, List.of(ID), List.of(), List.of(), "OPZMAIN", true);
        EntitySchema child = new EntitySchema(
            "id_user", "id_user", 1, List.of(ID, CODE), List.of(),
            List.of(new ForeignKeyDefinition("code", "company_information", "id",
                ForeignKeyDefinition.OnDelete.RESTRICT, ForeignKeyDefinition.Enforce.POSTGRES)),
            "OPZUSER", false);

        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(schemaRegistry.all()).thenReturn(List.of(parent, child));
        when(schemaRegistry.findByTable("company_information")).thenReturn(java.util.Optional.of(parent));
        when(dataClientRegistry.resolve(any(EntitySchema.class))).thenReturn(dataClient);
        when(introspector.introspect(dataClient, "company_information")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));
        when(introspector.introspect(dataClient, "id_user")).thenReturn(
            new SchemaIntrospector.LiveTable(false, Set.of(), Set.of(), Set.of()));

        reconciler.run(null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataClient, org.mockito.Mockito.atLeastOnce()).executeRaw(sqlCaptor.capture(), anyMap());
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("ADD CONSTRAINT"));
    }
}
