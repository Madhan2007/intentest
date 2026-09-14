/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Auto-configuration for Dashboard Layout module.
 */
package com.managemyopz.modules.dashboardlayout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.modules.dashboardlayout.api.DashboardCatalogController;
import com.managemyopz.modules.dashboardlayout.api.DashboardLayoutController;
import com.managemyopz.modules.dashboardlayout.application.DashboardLayoutService;
import com.managemyopz.modules.dashboardlayout.application.DashboardMergeService;
import com.managemyopz.modules.dashboardlayout.application.DashboardTemplateSeed;
import com.managemyopz.modules.dashboardlayout.data.*;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutConstants;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;

@Configuration
@ConditionalOnModule("dashboard-layout")
public class DashboardLayoutAutoConfiguration {

    @Bean
    public DataClient dashboardMainDataClient(DataClientRegistry dataClientRegistry) {
        return dataClientRegistry.forDatabase(DashboardLayoutConstants.DATABASE_MAIN);
    }

    @Bean
    public DataClient dashboardHubDataClient(DataClientRegistry dataClientRegistry) {
        return dataClientRegistry.forDatabase(DashboardLayoutConstants.DATABASE_HUB);
    }

    @Bean
    public WidgetCatalogRepository widgetCatalogRepository(DataClient dashboardMainDataClient) {
        return new DataClientWidgetCatalogRepository(dashboardMainDataClient);
    }

    @Bean
    public DashboardTemplateRepository dashboardTemplateRepository(DataClient dashboardMainDataClient) {
        return new DataClientDashboardTemplateRepository(dashboardMainDataClient);
    }

    @Bean
    public DashboardLayoutRepository dashboardLayoutRepository(DataClient dashboardHubDataClient) {
        return new DataClientDashboardLayoutRepository(dashboardHubDataClient);
    }

    @Bean
    public DashboardLayoutUserRepository dashboardLayoutUserRepository(DataClient dashboardHubDataClient) {
        return new DataClientDashboardLayoutUserRepository(dashboardHubDataClient);
    }

    @Bean
    public DashboardMergeService dashboardMergeService(ObjectMapper objectMapper) {
        return new DashboardMergeService(objectMapper);
    }

    @Bean
    public DashboardLayoutService dashboardLayoutService(
            DashboardLayoutRepository dashboardLayoutRepository,
            DashboardLayoutUserRepository dashboardLayoutUserRepository,
            DashboardTemplateRepository dashboardTemplateRepository,
            DashboardMergeService dashboardMergeService,
            ObjectMapper objectMapper) {
        return new DashboardLayoutService(dashboardLayoutRepository, dashboardLayoutUserRepository,
                dashboardTemplateRepository, dashboardMergeService, objectMapper);
    }

    @Bean
    public DashboardTemplateSeed dashboardTemplateSeed(
            WidgetCatalogRepository widgetCatalogRepository,
            DashboardTemplateRepository dashboardTemplateRepository,
            PlatformProperties platformProperties,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper) {
        return new DashboardTemplateSeed(widgetCatalogRepository, dashboardTemplateRepository,
                platformProperties, resourceLoader, objectMapper);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 30)
    public ApplicationRunner dashboardTemplateSeedRunner(DashboardTemplateSeed dashboardTemplateSeed) {
        return args -> dashboardTemplateSeed.seed();
    }

    @Bean
    public DashboardCatalogController dashboardCatalogController(
            WidgetCatalogRepository widgetCatalogRepository,
            DashboardTemplateRepository dashboardTemplateRepository) {
        return new DashboardCatalogController(widgetCatalogRepository, dashboardTemplateRepository);
    }

    @Bean
    public DashboardLayoutController dashboardLayoutController(
            DashboardLayoutService dashboardLayoutService,
            ObjectMapper objectMapper) {
        return new DashboardLayoutController(dashboardLayoutService, objectMapper);
    }
}
