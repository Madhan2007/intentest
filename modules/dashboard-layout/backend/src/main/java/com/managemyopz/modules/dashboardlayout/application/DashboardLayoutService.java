/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.modules.dashboardlayout.data.DashboardLayoutRepository;
import com.managemyopz.modules.dashboardlayout.data.DashboardLayoutUserRepository;
import com.managemyopz.modules.dashboardlayout.data.DashboardTemplateRepository;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayout;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutUser;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplate;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplateWidget;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardLayoutService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DashboardLayoutRepository layoutRepository;
    private final DashboardLayoutUserRepository userRepository;
    private final DashboardTemplateRepository templateRepository;
    private final DashboardMergeService mergeService;
    private final ObjectMapper objectMapper;

    public DashboardLayoutService(DashboardLayoutRepository layoutRepository,
                                  DashboardLayoutUserRepository userRepository,
                                  DashboardTemplateRepository templateRepository,
                                  DashboardMergeService mergeService,
                                  ObjectMapper objectMapper) {
        this.layoutRepository = layoutRepository;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.mergeService = mergeService;
        this.objectMapper = objectMapper;
    }

    public Optional<DashboardLayout> getRoleLayout(UUID companyId, String appKey, String roleKey) {
        return layoutRepository.findByCompanyAppRole(companyId, appKey, roleKey);
    }

    public void saveRoleLayout(UUID companyId, String appKey, String roleKey, String templateKey, String layoutDataJson, UUID updatedBy) {
        DashboardLayout layout = new DashboardLayout(
                UUID.randomUUID(),
                companyId,
                appKey,
                roleKey,
                templateKey,
                layoutDataJson,
                updatedBy,
                null
        );
        layoutRepository.upsert(layout);
    }

    public void applyTemplate(UUID companyId, String appKey, String roleKey, String templateKey, UUID updatedBy) {
        List<DashboardTemplateWidget> widgets = templateRepository.listWidgets(templateKey);
        String layoutDataJson = buildLayoutDataJson(widgets);
        saveRoleLayout(companyId, appKey, roleKey, templateKey, layoutDataJson, updatedBy);
    }

    public String getMyLayout(UUID userId, UUID companyId, String appKey, String primaryRole) {
        Optional<DashboardLayout> roleLayoutOpt = layoutRepository.findByCompanyAppRole(companyId, appKey, primaryRole);
        String roleLayoutJson = roleLayoutOpt.map(DashboardLayout::layoutDataJson).orElse(null);

        // Fallback to default template if no role layout exists
        if (roleLayoutJson == null || roleLayoutJson.isBlank()) {
            List<DashboardTemplate> templates = templateRepository.listByApp(appKey);
            if (!templates.isEmpty()) {
                String tKey = templates.getFirst().templateKey();
                List<DashboardTemplateWidget> widgets = templateRepository.listWidgets(tKey);
                roleLayoutJson = buildLayoutDataJson(widgets);
            } else {
                roleLayoutJson = "{\"zones\":{},\"grid_cols\":12,\"grid_row_height\":80}";
            }
        }

        Optional<DashboardLayoutUser> userLayoutOpt = userRepository.findByUserCompanyApp(userId, companyId, appKey);
        if (userLayoutOpt.isEmpty()) {
            return roleLayoutJson;
        }

        return mergeService.merge(roleLayoutJson, userLayoutOpt.get().layoutDataJson());
    }

    public void saveMyLayout(UUID userId, UUID companyId, String appKey, String layoutDataJson) {
        DashboardLayoutUser userLayout = new DashboardLayoutUser(
                UUID.randomUUID(),
                userId,
                companyId,
                appKey,
                layoutDataJson,
                null
        );
        userRepository.upsert(userLayout);
    }

    public void resetMyLayout(UUID userId, UUID companyId, String appKey) {
        userRepository.delete(userId, companyId, appKey);
    }

    private String buildLayoutDataJson(List<DashboardTemplateWidget> widgets) {
        Map<String, List<Map<String, Object>>> zones = new LinkedHashMap<>();
        for (DashboardTemplateWidget w : widgets) {
            zones.computeIfAbsent(w.zoneKey(), k -> new ArrayList<>());

            Map<String, Object> widgetItem = new LinkedHashMap<>();
            widgetItem.put("widget_key", w.widgetKey());

            try {
                if (w.positionJson() != null && !w.positionJson().isBlank()) {
                    Map<String, Object> pos = objectMapper.readValue(w.positionJson(), MAP_TYPE);
                    widgetItem.put("x", pos.getOrDefault("x", 0));
                    widgetItem.put("y", pos.getOrDefault("y", 0));
                    widgetItem.put("w", pos.getOrDefault("w", 4));
                    widgetItem.put("h", pos.getOrDefault("h", 2));
                }
            } catch (Exception ignored) {
                widgetItem.put("x", 0);
                widgetItem.put("y", 0);
                widgetItem.put("w", 4);
                widgetItem.put("h", 2);
            }

            try {
                if (w.widgetPropsJson() != null && !w.widgetPropsJson().isBlank()) {
                    widgetItem.put("props", objectMapper.readValue(w.widgetPropsJson(), MAP_TYPE));
                } else {
                    widgetItem.put("props", Map.of());
                }
            } catch (Exception ignored) {
                widgetItem.put("props", Map.of());
            }

            zones.get(w.zoneKey()).add(widgetItem);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("zones", zones);
        root.put("grid_cols", 12);
        root.put("grid_row_height", 80);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            return "{\"zones\":{},\"grid_cols\":12,\"grid_row_height\":80}";
        }
    }
}
