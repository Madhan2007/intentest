/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutUser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class DataClientDashboardLayoutUserRepository implements DashboardLayoutUserRepository {

    private final DataClient dataClient;

    public DataClientDashboardLayoutUserRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public Optional<DashboardLayoutUser> findByUserCompanyApp(UUID userId, UUID companyId, String appKey) {
        return dataClient.queryOne("dashboard_layout_user.find_by_user_company_app", Map.of(
                "user_id", userId,
                "company_id", companyId,
                "app_key", appKey
        )).map(this::mapRow);
    }

    @Override
    public void upsert(DashboardLayoutUser userLayout) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", userLayout.id() != null ? userLayout.id() : UUID.randomUUID());
        params.put("user_id", userLayout.userId());
        params.put("company_id", userLayout.companyId());
        params.put("app_key", userLayout.appKey());
        params.put("layout_data", userLayout.layoutDataJson() != null ? userLayout.layoutDataJson() : "{}");
        dataClient.execute("dashboard_layout_user.upsert", params);
    }

    @Override
    public void delete(UUID userId, UUID companyId, String appKey) {
        dataClient.execute("dashboard_layout_user.delete", Map.of(
                "user_id", userId,
                "company_id", companyId,
                "app_key", appKey
        ));
    }

    private DashboardLayoutUser mapRow(Row row) {
        return new DashboardLayoutUser(
                UUID.fromString(row.getString("id")),
                UUID.fromString(row.getString("user_id")),
                UUID.fromString(row.getString("company_id")),
                row.getString("app_key"),
                row.getString("layout_data"),
                row.getInstant("updated_at")
        );
    }
}
