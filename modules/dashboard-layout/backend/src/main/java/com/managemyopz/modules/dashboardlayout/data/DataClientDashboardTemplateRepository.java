/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplate;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplateWidget;

import java.util.*;

public class DataClientDashboardTemplateRepository implements DashboardTemplateRepository {

    private final DataClient dataClient;

    public DataClientDashboardTemplateRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public List<DashboardTemplate> listByApp(String appKey) {
        return dataClient.query("dashboard_template.list_by_app", Map.of("app_key", appKey))
                .stream().map(this::mapTemplate).toList();
    }

    @Override
    public Optional<DashboardTemplate> findByKey(String templateKey) {
        return dataClient.queryOne("dashboard_template.find_by_key", Map.of("template_key", templateKey))
                .map(this::mapTemplate);
    }

    @Override
    public void upsert(DashboardTemplate template) {
        Map<String, Object> params = new HashMap<>();
        params.put("template_key", template.templateKey());
        params.put("app_key", template.appKey());
        params.put("template_name", template.templateName());
        params.put("description", template.description() != null ? template.description() : "");
        params.put("thumbnail_uri", template.thumbnailUri() != null ? template.thumbnailUri() : "");
        params.put("is_builtin", template.isBuiltin());
        params.put("created_by", template.createdBy() != null ? template.createdBy() : "");
        dataClient.execute("dashboard_template.upsert", params);
    }

    @Override
    public List<DashboardTemplateWidget> listWidgets(String templateKey) {
        return dataClient.query("dashboard_template_widget.list_by_template", Map.of("template_key", templateKey))
                .stream().map(this::mapWidget).toList();
    }

    @Override
    public void replaceWidgets(String templateKey, List<DashboardTemplateWidget> widgets) {
        dataClient.execute("dashboard_template_widget.delete_by_template", Map.of("template_key", templateKey));
        for (DashboardTemplateWidget widget : widgets) {
            Map<String, Object> params = new HashMap<>();
            params.put("id", widget.id() != null ? widget.id() : UUID.randomUUID());
            params.put("template_key", templateKey);
            params.put("widget_key", widget.widgetKey());
            params.put("zone_key", widget.zoneKey());
            params.put("position", widget.positionJson() != null ? widget.positionJson() : "{}");
            params.put("widget_props", widget.widgetPropsJson() != null ? widget.widgetPropsJson() : "{}");
            dataClient.execute("dashboard_template_widget.insert", params);
        }
    }

    private DashboardTemplate mapTemplate(Row row) {
        return new DashboardTemplate(
                row.getString("template_key"),
                row.getString("app_key"),
                row.getString("template_name"),
                row.getString("description"),
                row.getString("thumbnail_uri"),
                Boolean.TRUE.equals(row.getBoolean("is_builtin")),
                row.get("created_by") != null && !row.getString("created_by").isBlank() ? UUID.fromString(row.getString("created_by")) : null,
                row.getInstant("created_at")
        );
    }

    private DashboardTemplateWidget mapWidget(Row row) {
        return new DashboardTemplateWidget(
                UUID.fromString(row.getString("id")),
                row.getString("template_key"),
                row.getString("widget_key"),
                row.getString("zone_key"),
                row.getString("position"),
                row.getString("widget_props")
        );
    }
}
