/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMergeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashboardMergeService mergeService = new DashboardMergeService(objectMapper);

    @Test
    @DisplayName("returns role layout unchanged when user override is absent")
    void returnsRoleLayoutWhenUserAbsent() {
        String roleJson = "{\"zones\":{\"header_bar\":[{\"widget_key\":\"mkt-kpi-leads\",\"w\":3}]},\"grid_cols\":12}";

        String merged = mergeService.merge(roleJson, null);

        assertThat(merged).isEqualTo(roleJson);
    }

    @Test
    @DisplayName("merges user position overrides while discarding widgets not permitted in role layout")
    void mergesAndTrimsDisallowedWidgets() throws Exception {
        String roleJson = """
            {
              "zones": {
                "header_bar": [{"widget_key": "mkt-kpi-leads", "x": 0, "w": 3}],
                "main_grid": [{"widget_key": "mkt-lead-funnel", "x": 0, "w": 6}]
              },
              "grid_cols": 12
            }
            """;

        String userJson = """
            {
              "zones": {
                "header_bar": [{"widget_key": "mkt-kpi-leads", "x": 4, "w": 4}],
                "main_grid": [
                  {"widget_key": "mkt-lead-funnel", "x": 6, "w": 6},
                  {"widget_key": "unauthorized-finance-widget", "x": 0, "w": 6}
                ]
              },
              "grid_cols": 12
            }
            """;

        String mergedJson = mergeService.merge(roleJson, userJson);

        Map<String, Object> result = objectMapper.readValue(mergedJson, new TypeReference<>() {});
        Map<String, Object> zones = (Map<String, Object>) result.get("zones");
        List<Map<String, Object>> mainGrid = (List<Map<String, Object>>) zones.get("main_grid");

        // Should retain mkt-lead-funnel with user positions, but discard unauthorized-finance-widget
        assertThat(mainGrid).hasSize(1);
        assertThat(mainGrid.getFirst().get("widget_key")).isEqualTo("mkt-lead-funnel");
        assertThat(mainGrid.getFirst().get("x")).isEqualTo(6);
    }
}
