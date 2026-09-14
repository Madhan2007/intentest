/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Audience;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Audience operations.
 */
public class DataClientAudienceRepository implements AudienceRepository {

    private final DataClient dataClient;

    public DataClientAudienceRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(Audience audience) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", audience.id());
        params.put("company_id", audience.companyId());
        params.put("audience_name", audience.audienceName());
        params.put("description", audience.description());
        params.put("rules_json", audience.rulesJson());

        dataClient.execute(MarketDataConstants.AUDIENCE_INSERT, params);
    }

    @Override
    public void update(Audience audience) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", audience.id());
        params.put("company_id", audience.companyId());
        params.put("audience_name", audience.audienceName());
        params.put("description", audience.description());
        params.put("rules_json", audience.rulesJson());

        dataClient.execute(MarketDataConstants.AUDIENCE_UPDATE, params);
    }

    @Override
    public List<Audience> listByCompany(String companyId) {
        return dataClient.query(MarketDataConstants.AUDIENCE_LIST_BY_COMPANY, Map.of("company_id", companyId)).stream()
            .map(DataClientAudienceRepository::audienceFromRow)
            .toList();
    }

    @Override
    public Optional<Audience> findById(String id, String companyId) {
        return listByCompany(companyId).stream().filter(a -> a.id().equals(id)).findFirst();
    }

    private static Audience audienceFromRow(Row row) {
        return new Audience(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("audience_name"),
            row.getString("description"),
            row.get("rules_json") != null ? row.get("rules_json").toString() : null,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
