/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadActivity;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Lead activity timeline operations.
 */
public class DataClientLeadActivityRepository implements LeadActivityRepository {

    private final DataClient dataClient;

    public DataClientLeadActivityRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(LeadActivity activity) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", activity.id());
        params.put("lead_id", activity.leadId());
        params.put("activity_type", activity.activityType());
        params.put("title", activity.title());
        params.put("description", activity.description());
        params.put("old_value", activity.oldValue());
        params.put("new_value", activity.newValue());
        params.put("related_call_log_id", activity.relatedCallLogId());
        params.put("related_followup_id", activity.relatedFollowupId());
        params.put("performed_by_user_id", activity.performedByUserId());

        dataClient.execute(MarketDataConstants.LEAD_ACTIVITY_INSERT, params);
    }

    @Override
    public List<LeadActivity> listByLeadPaged(String leadId, int limit, int offset) {
        Map<String, Object> params = Map.of(
            "lead_id", leadId,
            "limit", limit,
            "offset", offset
        );
        return dataClient.query(MarketDataConstants.LEAD_ACTIVITY_LIST_BY_LEAD_PAGED, params).stream()
            .map(DataClientLeadActivityRepository::activityFromRow)
            .toList();
    }

    private static LeadActivity activityFromRow(Row row) {
        return new LeadActivity(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getString("activity_type"),
            row.getString("title"),
            row.getString("description"),
            row.getString("old_value"),
            row.getString("new_value"),
            row.get("related_call_log_id") != null ? row.get("related_call_log_id").toString() : null,
            row.get("related_followup_id") != null ? row.get("related_followup_id").toString() : null,
            row.get("performed_by_user_id") != null ? row.get("performed_by_user_id").toString() : null,
            row.getInstant("created_at")
        );
    }
}
