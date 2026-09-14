/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadFollowup;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Lead follow-up operations.
 */
public class DataClientLeadFollowupRepository implements LeadFollowupRepository {

    private final DataClient dataClient;

    public DataClientLeadFollowupRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(LeadFollowup followup) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", followup.id());
        params.put("lead_id", followup.leadId());
        params.put("reason", followup.reason());
        params.put("scheduled_at", followup.scheduledAt());
        params.put("assigned_agent_user_id", followup.assignedAgentUserId());
        params.put("priority", followup.priority());
        params.put("status", followup.status());
        params.put("notes", followup.notes());

        dataClient.execute(MarketDataConstants.LEAD_FOLLOWUP_INSERT, params);
    }

    @Override
    public void update(LeadFollowup followup) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", followup.id());
        params.put("reason", followup.reason());
        params.put("scheduled_at", followup.scheduledAt());
        params.put("assigned_agent_user_id", followup.assignedAgentUserId());
        params.put("priority", followup.priority());
        params.put("status", followup.status());
        params.put("notes", followup.notes());

        dataClient.execute(MarketDataConstants.LEAD_FOLLOWUP_UPDATE, params);
    }

    @Override
    public List<LeadFollowup> listByLead(String leadId) {
        return dataClient.query(MarketDataConstants.LEAD_FOLLOWUP_LIST_BY_LEAD, Map.of("lead_id", leadId)).stream()
            .map(DataClientLeadFollowupRepository::followupFromRow)
            .toList();
    }

    @Override
    public List<LeadFollowup> listDue(String companyId, String assignedAgentUserId, Instant dueBefore) {
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("assigned_agent_user_id", assignedAgentUserId);
        params.put("due_before", dueBefore);

        return dataClient.query(MarketDataConstants.LEAD_FOLLOWUP_LIST_DUE, params).stream()
            .map(DataClientLeadFollowupRepository::followupFromRow)
            .toList();
    }

    private static LeadFollowup followupFromRow(Row row) {
        return new LeadFollowup(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getString("reason"),
            row.getInstant("scheduled_at"),
            row.get("assigned_agent_user_id") != null ? row.get("assigned_agent_user_id").toString() : null,
            row.getString("priority"),
            row.getString("status"),
            row.getString("notes"),
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
