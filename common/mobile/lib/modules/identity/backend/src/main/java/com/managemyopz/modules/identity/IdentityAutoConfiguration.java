/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.server.MemoryCommandRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.kernel.module.ModuleCatalog;
import com.managemyopz.modules.identity.api.IdentityController;
import com.managemyopz.modules.identity.application.AuthService;
import com.managemyopz.modules.identity.application.DefaultAdminBootstrap;
import com.managemyopz.modules.identity.data.DataClientIdentityRepository;
import com.managemyopz.modules.identity.data.IdentityMemoryStore;
import com.managemyopz.modules.identity.data.IdentityRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers only when the `identity` folder is present and enabled
 * (doc 01 §5 — Java Module SPI). Removing modules/identity removes this
 * whole @Configuration and every bean it declares.
 */
@Configuration
@ConditionalOnModule("identity")
public class IdentityAutoConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // OWASP-recommended Argon2id parameters (doc 18 §2.1: Argon2id compare).
        return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    }

    /**
     * Registers the identity query provider for the memory data client.
     *
     * @param registry memory command registry
     * @return identity memory-store query provider
     */
    @Bean
    public IdentityMemoryStore identityMemoryStore(MemoryCommandRegistry registry) {
        return new IdentityMemoryStore(registry);
    }

    @Bean
    public IdentityRepository identityRepository(DataClient dataClient) {
        return new DataClientIdentityRepository(dataClient);
    }

    @Bean
    public AuthService authService(IdentityRepository repository, PasswordEncoder passwordEncoder, CacheClient cacheClient,
                                    PlatformProperties platformProperties, ModuleCatalog moduleCatalog, ObjectMapper objectMapper) {
        return new AuthService(repository, passwordEncoder, cacheClient, platformProperties, moduleCatalog, objectMapper);
    }

    /**
     * Provides the one-time initial administrator bootstrap for PostgreSQL deployments.
     *
     * @param repository identity data repository
     * @param passwordEncoder Argon2id password encoder
     * @param platformProperties resolved platform configuration
     * @return bootstrap service
     */
    @Bean
    public DefaultAdminBootstrap defaultAdminBootstrap(
        IdentityRepository repository,
        PasswordEncoder passwordEncoder,
        PlatformProperties platformProperties
    ) {
        return new DefaultAdminBootstrap(repository, passwordEncoder, platformProperties);
    }

    /**
     * Runs initial-administrator bootstrap after application startup.
     *
     * @param defaultAdminBootstrap PostgreSQL bootstrap service
     * @return application startup runner
     */
    @Bean
    public ApplicationRunner defaultAdminBootstrapRunner(
        DefaultAdminBootstrap defaultAdminBootstrap
    ) {
        return arguments -> defaultAdminBootstrap.bootstrap();
    }

    @Bean
    public IdentityController identityController(AuthService authService) {
        return new IdentityController(authService);
    }
}
