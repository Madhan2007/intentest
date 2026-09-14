/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallLog;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Call log operations.
 */
public class DataClientCallLogRepository implements CallLogRepository {

    private final DataClient dataClient;

    public DataClientCallLogRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(CallLog callLog) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", callLog.id());
        params.put("company_id", callLog.companyId());
        params.put("queue_id", callLog.queueId());
        params.put("lead_id", callLog.leadId());
        params.put("agent_user_id", callLog.agentUserId());
        params.put("phone_number", callLog.phoneNumber());
        params.put("connection_status", callLog.connectionStatus());
        params.put("disposition", callLog.disposition());
        params.put("duration_seconds", callLog.durationSeconds());
        params.put("recording_url", callLog.recordingUrl());
        params.put("notes", callLog.notes());
        params.put("scheduled_followup_at", callLog.scheduledFollowupAt());

        dataClient.execute(MarketDataConstants.CALL_LOG_INSERT, params);
    }

    @Override
    public List<CallLog> listByLead(String leadId) {
        return dataClient.query(MarketDataConstants.CALL_LOG_LIST_BY_LEAD, Map.of("lead_id", leadId)).stream()
            .map(DataClientCallLogRepository::callLogFromRow)
            .toList();
    }

    private static CallLog callLogFromRow(Row row) {
        Object dur = row.get("duration_seconds");
        int durationSeconds = dur instanceof Number n ? n.intValue() : 0;

        return new CallLog(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.get("queue_id") != null ? row.get("queue_id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.get("agent_user_id") != null ? row.get("agent_user_id").toString() : null,
            row.getString("phone_number"),
            row.getString("connection_status"),
            row.getString("disposition"),
            durationSeconds,
            row.getString("recording_url"),
            row.getString("notes"),
            row.getInstant("scheduled_followup_at"),
            row.getInstant("created_at")
        );
    }
}
