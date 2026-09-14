/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DataClientDashboardLayoutRepository implements DashboardLayoutRepository {

    private final DataClient dataClient;

    public DataClientDashboardLayoutRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public Optional<DashboardLayout> findByCompanyAppRole(UUID companyId, String appKey, String roleKey) {
        return dataClient.queryOne("dashboard_layout.find_by_company_app_role", Map.of(
                "company_id", companyId,
                "app_key", appKey,
                "role_key", roleKey
        )).map(this::mapRow);
    }

    @Override
    public void upsert(DashboardLayout layout) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", layout.id() != null ? layout.id() : UUID.randomUUID());
        params.put("company_id", layout.companyId());
        params.put("app_key", layout.appKey());
        params.put("role_key", layout.roleKey());
        params.put("template_key", layout.templateKey() != null ? layout.templateKey() : "");
        params.put("layout_data", layout.layoutDataJson() != null ? layout.layoutDataJson() : "{}");
        params.put("updated_by", layout.updatedBy());
        dataClient.execute("dashboard_layout.upsert", params);
    }

    private DashboardLayout mapRow(Row row) {
        return new DashboardLayout(
                UUID.fromString(row.getString("id")),
                UUID.fromString(row.getString("company_id")),
                row.getString("app_key"),
                row.getString("role_key"),
                row.getString("template_key"),
                row.getString("layout_data"),
                UUID.fromString(row.getString("updated_by")),
                row.getInstant("updated_at")
        );
    }
}
