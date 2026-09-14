/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Journey;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Journey operations.
 */
public class DataClientJourneyRepository implements JourneyRepository {

    private final DataClient dataClient;

    public DataClientJourneyRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(Journey journey) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", journey.id());
        params.put("company_id", journey.companyId());
        params.put("journey_name", journey.journeyName());
        params.put("status", journey.status());
        params.put("trigger_type", journey.triggerType());
        params.put("flow_definition_json", journey.flowDefinitionJson());

        dataClient.execute(MarketDataConstants.JOURNEY_INSERT, params);
    }

    @Override
    public void update(Journey journey) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", journey.id());
        params.put("company_id", journey.companyId());
        params.put("journey_name", journey.journeyName());
        params.put("status", journey.status());
        params.put("trigger_type", journey.triggerType());
        params.put("flow_definition_json", journey.flowDefinitionJson());

        dataClient.execute(MarketDataConstants.JOURNEY_UPDATE, params);
    }

    @Override
    public List<Journey> listByCompany(String companyId) {
        return dataClient.query(MarketDataConstants.JOURNEY_LIST_BY_COMPANY, Map.of("company_id", companyId)).stream()
            .map(DataClientJourneyRepository::journeyFromRow)
            .toList();
    }

    @Override
    public Optional<Journey> findById(String id, String companyId) {
        return listByCompany(companyId).stream().filter(j -> j.id().equals(id)).findFirst();
    }

    private static Journey journeyFromRow(Row row) {
        return new Journey(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("journey_name"),
            row.getString("status"),
            row.getString("trigger_type"),
            row.get("flow_definition_json") != null ? row.get("flow_definition_json").toString() : null,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
