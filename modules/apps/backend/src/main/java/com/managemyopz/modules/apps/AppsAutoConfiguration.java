/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Registers the OPZHUB application catalog module when enabled.
 */
package com.managemyopz.modules.apps;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.schema.ForeignKeyValidator;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.modules.apps.api.AppsController;
import com.managemyopz.modules.apps.application.ApplicationCatalogSeed;
import com.managemyopz.modules.apps.application.ApplicationFolderPresence;
import com.managemyopz.modules.apps.application.CompanyApplicationService;
import com.managemyopz.modules.apps.application.PlatformProductSeed;
import com.managemyopz.modules.apps.data.CompanyApplicationRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;

/**
 * Registers only when the {@code apps} folder is present and enabled.
 */
@Configuration
@ConditionalOnModule("apps")
public class AppsAutoConfiguration {

    /**
     * Schema-driven catalog and company application store.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     * @param foreignKeyValidator app-level foreign-key checks
     * @return application repository
     */
    @Bean
    public CompanyApplicationRepository companyApplicationRepository(
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry,
        ForeignKeyValidator foreignKeyValidator
    ) {
        return new CompanyApplicationRepository(schemaRegistry, dataClientRegistry, foreignKeyValidator);
    }

    /**
     * Checks whether {@code apps/<app_key>} exists for license marking.
     *
     * @return folder presence checker
     */
    @Bean
    public ApplicationFolderPresence applicationFolderPresence() {
        return new ApplicationFolderPresence();
    }

    /**
     * Company-scoped license and install transitions.
     *
     * @param companyApplicationRepository application store
     * @param applicationFolderPresence apps/ folder checker
     * @return application service
     */
    @Bean
    public CompanyApplicationService companyApplicationService(
        CompanyApplicationRepository companyApplicationRepository,
        ApplicationFolderPresence applicationFolderPresence
    ) {
        return new CompanyApplicationService(companyApplicationRepository, applicationFolderPresence);
    }

    /**
     * Seeds the product catalog after schema reconciliation.
     *
     * @param companyApplicationRepository application store
     * @param platformProperties resolved platform configuration
     * @param resourceLoader classpath resource loader
     * @param schemaRegistry loaded entity schemas (for OPZMAIN product_catalog)
     * @param dataClientRegistry named-database router
     * @return catalog seed
     */
    @Bean
    public ApplicationCatalogSeed applicationCatalogSeed(
        CompanyApplicationRepository companyApplicationRepository,
        PlatformProperties platformProperties,
        ResourceLoader resourceLoader,
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry
    ) {
        return new ApplicationCatalogSeed(
            companyApplicationRepository, platformProperties, resourceLoader, schemaRegistry, dataClientRegistry);
    }

    /**
     * Runs catalog seed at startup.
     *
     * @param applicationCatalogSeed catalog seed
     * @return startup runner
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public ApplicationRunner applicationCatalogSeedRunner(ApplicationCatalogSeed applicationCatalogSeed) {
        return arguments -> applicationCatalogSeed.seed();
    }

    /**
     * Records which product this deployment was packaged for (doc 35 §3.5).
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     * @return platform product seed
     */
    @Bean
    public PlatformProductSeed platformProductSeed(
        SchemaRegistry schemaRegistry,
        DataClientRegistry dataClientRegistry
    ) {
        return new PlatformProductSeed(schemaRegistry, dataClientRegistry);
    }

    /**
     * Runs the platform product seed at startup, after the application
     * catalog seed has seeded product_catalog (the FK dependency).
     *
     * @param platformProductSeed platform product seed
     * @return startup runner
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 20)
    public ApplicationRunner platformProductSeedRunner(PlatformProductSeed platformProductSeed) {
        return arguments -> platformProductSeed.seed();
    }

    /**
     * Dedicated applications API.
     *
     * @param companyApplicationService company application service
     * @return applications controller
     */
    @Bean
    public AppsController appsController(CompanyApplicationService companyApplicationService) {
        return new AppsController(companyApplicationService);
    }
}
