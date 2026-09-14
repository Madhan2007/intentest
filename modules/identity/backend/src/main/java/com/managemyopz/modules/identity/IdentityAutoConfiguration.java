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
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.server.MemoryCommandRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.kernel.module.ModuleCatalog;
import com.managemyopz.modules.identity.api.IdentityController;
import com.managemyopz.modules.identity.application.AuthService;
import com.managemyopz.modules.identity.application.DefaultAdminBootstrap;
import com.managemyopz.modules.identity.application.DefaultPlatformAdminBootstrap;
import com.managemyopz.modules.identity.data.DataClientIdentityRepository;
import com.managemyopz.modules.identity.data.DataClientPlatformAdminRepository;
import com.managemyopz.modules.identity.data.IdentityDataConstants;
import com.managemyopz.modules.identity.data.IdentityMemoryStore;
import com.managemyopz.modules.identity.data.IdentityRepository;
import com.managemyopz.modules.identity.data.PlatformAdminRepository;
import com.managemyopz.modules.identity.data.SchemaCompanyDirectory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    /**
     * Identity's data lives in its own database ({@link IdentityDataConstants#DATABASE_NAME}),
     * not the default connection — resolved once here via {@link DataClientRegistry}.
     *
     * @param dataClientRegistry named-database registry
     * @return identity repository bound to OPZUSER
     */
    @Bean
    public IdentityRepository identityRepository(
        DataClientRegistry dataClientRegistry,
        SchemaRegistry schemaRegistry
    ) {
        return new DataClientIdentityRepository(
            dataClientRegistry.forDatabase(IdentityDataConstants.DATABASE_NAME),
            schemaRegistry
        );
    }

    /**
     * Resolves login prefixes and email domains against company_references.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     * @return company reference directory
     */
    @Bean
    public SchemaCompanyDirectory schemaCompanyDirectory(
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry
    ) {
        return new SchemaCompanyDirectory(schemaRegistry, dataClientRegistry);
    }

    /**
     * Platform admin (doc 35 §2) accounts live in OPZMAIN's platform_admin
     * table, never in OPZUSER — resolved once here via {@link DataClientRegistry},
     * bound to a different database than {@link #identityRepository}.
     *
     * @param dataClientRegistry named-database registry
     * @return platform admin repository bound to OPZMAIN
     */
    @Bean
    public PlatformAdminRepository platformAdminRepository(DataClientRegistry dataClientRegistry) {
        return new DataClientPlatformAdminRepository(
            dataClientRegistry.forDatabase(IdentityDataConstants.PLATFORM_ADMIN_DATABASE_NAME)
        );
    }

    @Bean
    public AuthService authService(
        IdentityRepository repository,
        PlatformAdminRepository platformAdminRepository,
        SchemaCompanyDirectory companyDirectory,
        PasswordEncoder passwordEncoder,
        CacheClient cacheClient,
        PlatformProperties platformProperties,
        ModuleCatalog moduleCatalog,
        ObjectMapper objectMapper
    ) {
        return new AuthService(
            repository,
            platformAdminRepository,
            companyDirectory,
            passwordEncoder,
            cacheClient,
            platformProperties,
            moduleCatalog,
            objectMapper
        );
    }

    /**
     * Provides the one-time initial administrator bootstrap for PostgreSQL deployments.
     *
     * @param repository identity data repository
     * @param companyDirectory company reference directory
     * @param passwordEncoder Argon2id password encoder
     * @param platformProperties resolved platform configuration
     * @return bootstrap service
     */
    @Bean
    public DefaultAdminBootstrap defaultAdminBootstrap(
        IdentityRepository repository,
        SchemaCompanyDirectory companyDirectory,
        PasswordEncoder passwordEncoder,
        PlatformProperties platformProperties
    ) {
        return new DefaultAdminBootstrap(repository, companyDirectory, passwordEncoder, platformProperties);
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

    /**
     * Provides the one-time initial platform administrator bootstrap
     * (doc 35 §2) for PostgreSQL deployments.
     *
     * @param platformAdminRepository OPZMAIN platform_admin repository
     * @param passwordEncoder Argon2id password encoder
     * @param platformProperties resolved platform configuration
     * @return bootstrap service
     */
    @Bean
    public DefaultPlatformAdminBootstrap defaultPlatformAdminBootstrap(
        PlatformAdminRepository platformAdminRepository,
        PasswordEncoder passwordEncoder,
        PlatformProperties platformProperties
    ) {
        return new DefaultPlatformAdminBootstrap(platformAdminRepository, passwordEncoder, platformProperties);
    }

    /**
     * Runs initial-platform-administrator bootstrap after application startup.
     *
     * @param defaultPlatformAdminBootstrap PostgreSQL bootstrap service
     * @return application startup runner
     */
    @Bean
    public ApplicationRunner defaultPlatformAdminBootstrapRunner(
        DefaultPlatformAdminBootstrap defaultPlatformAdminBootstrap
    ) {
        return arguments -> defaultPlatformAdminBootstrap.bootstrap();
    }

    @Bean
    public IdentityController identityController(AuthService authService) {
        return new IdentityController(authService);
    }
}
