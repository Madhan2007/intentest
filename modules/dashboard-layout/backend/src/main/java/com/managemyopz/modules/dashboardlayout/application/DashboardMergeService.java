/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardMergeService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public DashboardMergeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Merges a base role layout with user overrides.
     * Rules:
     * 1. If user layout is empty, returns role layout.
     * 2. If role layout is empty, returns user layout.
     * 3. For each widget in user layout, if it exists in role layout, user positions override role positions.
     * 4. User widgets not present in the role layout are discarded (security trim).
     */
    @SuppressWarnings("unchecked")
    public String merge(String roleLayoutJson, String userLayoutJson) {
        if (userLayoutJson == null || userLayoutJson.isBlank() || "{}".equals(userLayoutJson.trim())) {
            return roleLayoutJson;
        }
        if (roleLayoutJson == null || roleLayoutJson.isBlank() || "{}".equals(roleLayoutJson.trim())) {
            return userLayoutJson;
        }

        try {
            Map<String, Object> roleLayout = objectMapper.readValue(roleLayoutJson, MAP_TYPE);
            Map<String, Object> userLayout = objectMapper.readValue(userLayoutJson, MAP_TYPE);

            Map<String, Object> roleZones = (Map<String, Object>) roleLayout.get("zones");
            Map<String, Object> userZones = (Map<String, Object>) userLayout.get("zones");

            if (roleZones == null || userZones == null) {
                return userLayoutJson;
            }

            // Collect all allowed widget keys from role layout
            Set<String> allowedWidgetKeys = new HashSet<>();
            for (Object zoneListObj : roleZones.values()) {
                if (zoneListObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m && m.get("widget_key") != null) {
                            allowedWidgetKeys.add(m.get("widget_key").toString());
                        }
                    }
                }
            }

            // Filter user layout zones to only retain allowed widgets
            Map<String, Object> mergedZones = new HashMap<>();
            for (Map.Entry<String, Object> entry : userZones.entrySet()) {
                String zoneKey = entry.getKey();
                if (entry.getValue() instanceof List<?> list) {
                    List<Map<String, Object>> keptWidgets = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m && m.get("widget_key") != null) {
                            String widgetKey = m.get("widget_key").toString();
                            if (allowedWidgetKeys.contains(widgetKey)) {
                                keptWidgets.add((Map<String, Object>) m);
                            }
                        }
                    }
                    mergedZones.put(zoneKey, keptWidgets);
                }
            }

            Map<String, Object> result = new HashMap<>(roleLayout);
            result.put("zones", mergedZones);
            if (userLayout.containsKey("grid_cols")) {
                result.put("grid_cols", userLayout.get("grid_cols"));
            }
            if (userLayout.containsKey("grid_row_height")) {
                result.put("grid_row_height", userLayout.get("grid_row_height"));
            }

            return objectMapper.writeValueAsString(result);
        } catch (Exception ex) {
            // Fallback to role layout on parsing error
            return roleLayoutJson;
        }
    }
}
