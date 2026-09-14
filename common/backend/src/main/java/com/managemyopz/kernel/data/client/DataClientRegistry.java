/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.server.CommandCatalog;
import com.managemyopz.kernel.data.server.PostgresDataServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes to one of several physical Postgres databases declared under
 * platform.yaml's {@code databases:} block (e.g. OPZUSER, OPZMAIN), each built as
 * an independent {@link PostgresDataServer} sharing the kernel-wide
 * {@link CommandCatalog} singleton. The pre-existing single {@code DataClient} bean
 * (built by DataClientConfig) remains the default connection for anything that
 * does not declare a {@code database:}.
 *
 * <p>In memory mode ({@code db.type=memory}) every name resolves back to that
 * same single default client — there is no per-database memory equivalent, and
 * none is needed: the shared MemoryDataServer/MemoryCommandRegistry already
 * answers every module's command names regardless of which physical database
 * that module would target in postgres mode. This is what keeps identity's
 * memory-mode login flow working unchanged after its data moves to OPZUSER.
 */
@Component
public class DataClientRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataClientRegistry.class);
    private static final String POSTGRES_DB_TYPE = "postgres";

    private final DataClient defaultClient;
    private final Map<String, DataClient> named = new LinkedHashMap<>();

    public DataClientRegistry(DataClient defaultClient, PlatformProperties platformProperties, CommandCatalog commandCatalog) {
        this.defaultClient = defaultClient;
        if (POSTGRES_DB_TYPE.equals(platformProperties.getDb().getType())) {
            for (Map.Entry<String, Map<String, Object>> entry : platformProperties.getDatabases().entrySet()) {
                named.put(entry.getKey(), new PostgresDataServer(entry.getValue(), commandCatalog));
            }
        }
    }

    /** Resolves the connection a given entity's schema targets. */
    public DataClient resolve(EntitySchema schema) {
        return schema.database() == null ? defaultClient : forDatabase(schema.database());
    }

    /**
     * Resolves a named connection directly (for non-schema-driven callers, e.g.
     * identity). Falls back to the default client for an unknown name or in
     * memory mode — never throws, so a module never loses its data path just
     * because {@code db.type=memory} or a database wasn't declared.
     */
    public DataClient forDatabase(String name) {
        if (name == null) {
            return defaultClient;
        }
        return named.getOrDefault(name, defaultClient);
    }

    /**
     * Closes named-database pools created by this registry. The default
     * {@link DataClient} bean is owned by Spring and closed separately.
     */
    @Override
    public void close() {
        for (Map.Entry<String, DataClient> entry : named.entrySet()) {
            DataClient client = entry.getValue();
            if (client == defaultClient || !(client instanceof AutoCloseable closeable)) {
                continue;
            }
            try {
                closeable.close();
            } catch (Exception exception) {
                log.warn("Failed to close data client for database {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        named.clear();
    }
}
