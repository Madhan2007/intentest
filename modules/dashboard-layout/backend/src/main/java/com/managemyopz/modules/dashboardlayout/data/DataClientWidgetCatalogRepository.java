/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.modules.dashboardlayout.domain.WidgetCatalogItem;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;

public class DataClientWidgetCatalogRepository implements WidgetCatalogRepository {

    private final DataClient dataClient;

    public DataClientWidgetCatalogRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public List<WidgetCatalogItem> listByApp(String appKey) {
        return dataClient.query("widget_catalog.list_by_app", Map.of("app_key", appKey))
                .stream().map(this::mapRow).toList();
    }

    @Override
    public void upsert(WidgetCatalogItem item) {
        Map<String, Object> params = new HashMap<>();
        params.put("widget_key", item.widgetKey());
        params.put("app_key", item.appKey());
        params.put("widget_name", item.widgetName());
        params.put("description", item.description() != null ? item.description() : "");
        params.put("icon_key", item.iconKey() != null ? item.iconKey() : "");
        params.put("default_zone", item.defaultZone());
        params.put("default_w", item.defaultW());
        params.put("default_h", item.defaultH());
        params.put("min_w", item.minW());
        params.put("min_h", item.minH());
        params.put("props_schema", item.propsSchema() != null ? item.propsSchema() : "{}");
        String[] rolesArray = item.allowedRoles() != null ? item.allowedRoles().toArray(new String[0]) : new String[0];
        params.put("allowed_roles", rolesArray);
        params.put("is_system", item.isSystem());
        params.put("status", item.status() != null ? item.status() : "active");
        dataClient.execute("widget_catalog.upsert", params);
    }

    private WidgetCatalogItem mapRow(Row row) {
        return new WidgetCatalogItem(
                row.getString("widget_key"),
                row.getString("app_key"),
                row.getString("widget_name"),
                row.getString("description"),
                row.getString("icon_key"),
                row.getString("default_zone"),
                row.getLong("default_w") != null ? row.getLong("default_w").intValue() : 4,
                row.getLong("default_h") != null ? row.getLong("default_h").intValue() : 2,
                row.getLong("min_w") != null ? row.getLong("min_w").intValue() : 2,
                row.getLong("min_h") != null ? row.getLong("min_h").intValue() : 1,
                row.getString("props_schema"),
                rolesFrom(row.get("allowed_roles")),
                Boolean.TRUE.equals(row.getBoolean("is_system")),
                row.getString("status"),
                row.getInstant("created_at")
        );
    }

    private static List<String> rolesFrom(Object rolesValue) {
        if (rolesValue == null) return List.of();
        if (rolesValue instanceof List<?> list) {
            List<String> roles = new ArrayList<>();
            for (Object role : list) {
                if (role != null) roles.add(role.toString());
            }
            return List.copyOf(roles);
        }
        if (rolesValue instanceof String[] array) {
            return List.of(array);
        }
        if (rolesValue instanceof Object[] array) {
            List<String> roles = new ArrayList<>();
            for (Object role : array) {
                if (role != null) roles.add(role.toString());
            }
            return List.copyOf(roles);
        }
        if (rolesValue instanceof Array jdbcArray) {
            try {
                return rolesFrom(jdbcArray.getArray());
            } catch (SQLException exception) {
                return List.of();
            }
        }
        return List.of();
    }
}
