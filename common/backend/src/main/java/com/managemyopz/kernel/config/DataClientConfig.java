/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.config;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.server.CommandCatalog;
import com.managemyopz.kernel.data.server.MemoryCommandRegistry;
import com.managemyopz.kernel.data.server.MemoryDataServer;
import com.managemyopz.kernel.data.server.PostgresDataServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * db.type factory (doc 07 §8). Unknown type is a fatal boot error — no
 * silent fallthrough to another server.
 */
@Configuration
public class DataClientConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public DataClient dataClient(PlatformProperties props, CommandCatalog commandCatalog, MemoryCommandRegistry memoryCommandRegistry) {
        String type = props.getDb().getType();
        return switch (type) {
            case "postgres" -> new PostgresDataServer(props.getDb().getPostgres(), commandCatalog);
            case "memory" -> new MemoryDataServer(memoryCommandRegistry);
            default -> throw new IllegalStateException("Unknown db.type: " + type);
        };
    }
}
