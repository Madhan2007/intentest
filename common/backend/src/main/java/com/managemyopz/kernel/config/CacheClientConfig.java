/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.config;

import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.cache.server.MemoryCacheServer;
import com.managemyopz.kernel.cache.server.ValkeyCacheServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** cache.type factory (doc 07 §8). */
@Configuration
public class CacheClientConfig {

    @Bean
    public CacheClient cacheClient(PlatformProperties props) {
        String type = props.getCache().getType();
        return switch (type) {
            case "valkey" -> new ValkeyCacheServer(props.getCache().getValkey());
            case "memory" -> new MemoryCacheServer();
            default -> throw new IllegalStateException("Unknown cache.type: " + type);
        };
    }
}
