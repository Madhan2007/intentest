/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.modules.dashboardlayout.data.DashboardTemplateRepository;
import com.managemyopz.modules.dashboardlayout.data.WidgetCatalogRepository;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplate;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplateWidget;
import com.managemyopz.modules.dashboardlayout.domain.WidgetCatalogItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

@Component
public class DashboardTemplateSeed {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardTemplateSeed.class);
    private static final String TEMPLATES_RESOURCE = "classpath:modules/dashboard-layout/db/seed/templates.yaml";
    private static final String MARKET_WIDGETS_RESOURCE = "classpath:modules/manage-my-market/db/seed/market_dashboard_widgets.yaml";

    private final WidgetCatalogRepository widgetCatalogRepository;
    private final DashboardTemplateRepository templateRepository;
    private final PlatformProperties platformProperties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public DashboardTemplateSeed(WidgetCatalogRepository widgetCatalogRepository,
                                 DashboardTemplateRepository templateRepository,
                                 PlatformProperties platformProperties,
                                 ResourceLoader resourceLoader,
                                 ObjectMapper objectMapper) {
        this.widgetCatalogRepository = widgetCatalogRepository;
        this.templateRepository = templateRepository;
        this.platformProperties = platformProperties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public void seed() {
        if (!"postgres".equalsIgnoreCase(platformProperties.getDb().getType())) {
            LOGGER.info("DashboardTemplateSeed skipped (db.type={})", platformProperties.getDb().getType());
            return;
        }

        seedMarketWidgets();
        seedTemplates();
    }

    @SuppressWarnings("unchecked")
    private void seedMarketWidgets() {
        try {
            Resource res = resourceLoader.getResource(MARKET_WIDGETS_RESOURCE);
            if (!res.exists()) {
                LOGGER.debug("Market widgets resource not found: {}", MARKET_WIDGETS_RESOURCE);
                return;
            }
            try (InputStream in = res.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(in);
                if (data != null && data.get("widgets") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            String key = String.valueOf(m.get("widget_key"));
                            String appKey = String.valueOf(m.get("app_key"));
                            String name = String.valueOf(m.get("widget_name"));
                            String desc = m.get("description") != null ? String.valueOf(m.get("description")) : "";
                            String icon = m.get("icon_key") != null ? String.valueOf(m.get("icon_key")) : "";
                            String zone = m.get("default_zone") != null ? String.valueOf(m.get("default_zone")) : "main_grid";
                            int defW = m.get("default_w") instanceof Number n ? n.intValue() : 4;
                            int defH = m.get("default_h") instanceof Number n ? n.intValue() : 2;
                            int minW = m.get("min_w") instanceof Number n ? n.intValue() : 2;
                            int minH = m.get("min_h") instanceof Number n ? n.intValue() : 1;
                            List<String> roles = m.get("allowed_roles") instanceof List<?> rList ? rList.stream().map(Object::toString).toList() : List.of();
                            boolean isSys = Boolean.TRUE.equals(m.get("is_system"));
                            String status = m.get("status") != null ? String.valueOf(m.get("status")) : "active";

                            widgetCatalogRepository.upsert(new WidgetCatalogItem(
                                    key, appKey, name, desc, icon, zone, defW, defH, minW, minH, "{}", roles, isSys, status, null
                            ));
                        }
                    }
                    LOGGER.info("Seeded {} market dashboard widgets", list.size());
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed seeding market widgets: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void seedTemplates() {
        try {
            Resource res = resourceLoader.getResource(TEMPLATES_RESOURCE);
            if (!res.exists()) {
                return;
            }
            try (InputStream in = res.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(in);
                if (data != null && data.get("templates") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            String tKey = String.valueOf(m.get("template_key"));
                            String appKey = String.valueOf(m.get("app_key"));
                            String name = String.valueOf(m.get("template_name"));
                            String desc = m.get("description") != null ? String.valueOf(m.get("description")) : "";
                            String thumb = m.get("thumbnail_uri") != null ? String.valueOf(m.get("thumbnail_uri")) : "";

                            templateRepository.upsert(new DashboardTemplate(
                                    tKey, appKey, name, desc, thumb, true, null, null
                            ));

                            if (m.get("widgets") instanceof List<?> wList) {
                                List<DashboardTemplateWidget> dtwList = new ArrayList<>();
                                for (Object wItem : wList) {
                                    if (wItem instanceof Map<?, ?> wm) {
                                        String wKey = String.valueOf(wm.get("widget_key"));
                                        String zKey = wm.get("zone_key") != null ? String.valueOf(wm.get("zone_key")) : "main_grid";
                                        Map<String, Object> pos = new LinkedHashMap<>();
                                        pos.put("x", wm.get("x") != null ? wm.get("x") : 0);
                                        pos.put("y", wm.get("y") != null ? wm.get("y") : 0);
                                        pos.put("w", wm.get("w") != null ? wm.get("w") : 4);
                                        pos.put("h", wm.get("h") != null ? wm.get("h") : 2);
                                        dtwList.add(new DashboardTemplateWidget(
                                                UUID.randomUUID(),
                                                tKey,
                                                wKey,
                                                zKey,
                                                objectMapper.writeValueAsString(pos),
                                                "{}"
                                        ));
                                    }
                                }
                                templateRepository.replaceWidgets(tKey, dtwList);
                            }
                        }
                    }
                    LOGGER.info("Seeded {} dashboard templates", list.size());
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed seeding dashboard templates: {}", ex.getMessage());
        }
    }
}
