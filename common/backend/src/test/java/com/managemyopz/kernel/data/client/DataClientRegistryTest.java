/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.schema.ColumnDefinition;
import com.managemyopz.kernel.data.schema.ColumnType;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.server.CommandCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataClientRegistryTest {

    private static final EntitySchema DEFAULT_SCHEMA = schema(null);
    private static final EntitySchema NAMED_SCHEMA = schema("OPZUSER");

    @Mock private DataClient defaultClient;
    @Mock private PlatformProperties platformProperties;
    @Mock private PlatformProperties.Db db;
    @Mock private CommandCatalog commandCatalog;

    @Test
    @DisplayName("memory mode: every name resolves back to the single default client, never throws")
    void memoryModeAlwaysReturnsDefaultClient() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("memory");
        // A `databases:` block may still be present in platform.yaml even in memory
        // mode; the constructor must not even look at it (never calls getDatabases()).

        DataClientRegistry registry = new DataClientRegistry(defaultClient, platformProperties, commandCatalog);

        assertThat(registry.forDatabase("OPZUSER")).isSameAs(defaultClient);
        assertThat(registry.forDatabase("ANYTHING_UNDECLARED")).isSameAs(defaultClient);
        assertThat(registry.resolve(DEFAULT_SCHEMA)).isSameAs(defaultClient);
        assertThat(registry.resolve(NAMED_SCHEMA)).isSameAs(defaultClient);
    }

    @Test
    @DisplayName("postgres mode: an entity with no declared database uses the default connection")
    void postgresModeNoDatabaseUsesDefault() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(platformProperties.getDatabases()).thenReturn(Map.of());

        DataClientRegistry registry = new DataClientRegistry(defaultClient, platformProperties, commandCatalog);

        assertThat(registry.resolve(DEFAULT_SCHEMA)).isSameAs(defaultClient);
    }

    @Test
    @DisplayName("postgres mode: an unconfigured named database falls back to the default connection")
    void postgresModeUnknownNameFallsBackToDefault() {
        when(platformProperties.getDb()).thenReturn(db);
        when(db.getType()).thenReturn("postgres");
        when(platformProperties.getDatabases()).thenReturn(Map.of());

        DataClientRegistry registry = new DataClientRegistry(defaultClient, platformProperties, commandCatalog);

        assertThat(registry.forDatabase("OPZUSER")).isSameAs(defaultClient);
    }

    private static EntitySchema schema(String database) {
        return new EntitySchema(
            "widget", "widget", 1,
            List.of(new ColumnDefinition("id", ColumnType.UUID, false, false, true, null, null, null, null)),
            List.of(), List.of(), database, true
        );
    }
}
