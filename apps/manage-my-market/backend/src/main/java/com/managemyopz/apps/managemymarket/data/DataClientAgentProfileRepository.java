/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.AgentProfile;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Agent profile operations.
 */
public class DataClientAgentProfileRepository implements AgentProfileRepository {

    private final DataClient dataClient;

    public DataClientAgentProfileRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void upsert(AgentProfile profile) {
        Map<String, Object> params = new HashMap<>();
        params.put("agent_user_id", profile.agentUserId());
        params.put("company_id", profile.companyId());
        params.put("marketing_type", profile.marketingType());
        params.put("linked_person_id", profile.linkedPersonId());

        dataClient.execute(MarketDataConstants.AGENT_PROFILE_UPSERT, params);
    }

    @Override
    public Optional<AgentProfile> findByUserId(String agentUserId) {
        return dataClient.queryOne(MarketDataConstants.AGENT_PROFILE_FIND_BY_USER_ID, Map.of("agent_user_id", agentUserId))
            .map(DataClientAgentProfileRepository::profileFromRow);
    }

    private static AgentProfile profileFromRow(Row row) {
        return new AgentProfile(
            row.get("agent_user_id") != null ? row.get("agent_user_id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("marketing_type"),
            row.get("linked_person_id") != null ? row.get("linked_person_id").toString() : null,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
