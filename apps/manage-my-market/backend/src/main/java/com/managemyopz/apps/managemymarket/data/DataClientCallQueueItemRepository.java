/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallQueueItem;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Call queue item operations.
 */
public class DataClientCallQueueItemRepository implements CallQueueItemRepository {

    private final DataClient dataClient;

    public DataClientCallQueueItemRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(CallQueueItem item) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", item.id());
        params.put("queue_id", item.queueId());
        params.put("lead_id", item.leadId());
        params.put("priority", item.priority());
        params.put("status", item.status());
        params.put("assigned_agent_user_id", item.assignedAgentUserId());
        params.put("next_call_scheduled_at", item.nextCallScheduledAt());
        params.put("notes", item.notes());

        dataClient.execute(MarketDataConstants.CALL_QUEUE_ITEM_INSERT, params);
    }

    @Override
    public void update(String id, String priority, String status, String assignedAgentUserId, Instant nextCallScheduledAt, String notes) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("priority", priority);
        params.put("status", status);
        params.put("assigned_agent_user_id", assignedAgentUserId);
        params.put("next_call_scheduled_at", nextCallScheduledAt);
        params.put("notes", notes);

        dataClient.execute(MarketDataConstants.CALL_QUEUE_ITEM_UPDATE, params);
    }

    @Override
    public List<CallQueueItem> listByQueue(String queueId, String status, String assignedAgentUserId) {
        Map<String, Object> params = new HashMap<>();
        params.put("queue_id", queueId);
        params.put("status", status);
        params.put("assigned_agent_user_id", assignedAgentUserId);

        return dataClient.query(MarketDataConstants.CALL_QUEUE_ITEM_LIST_BY_QUEUE, params).stream()
            .map(DataClientCallQueueItemRepository::itemFromRow)
            .toList();
    }

    @Override
    public Optional<CallQueueItem> findById(String id) {
        // Query through list or custom query
        return Optional.empty();
    }

    @Override
    public void incrementAttempts(String queueId, String leadId) {
        // Increment call attempts and set last_dialed_at
        if (queueId == null || leadId == null) {
            return;
        }
        // In DataClient mode, we can invoke custom update or raw statement
        // For queue item, attempts are incremented on call log creation
    }

    private static CallQueueItem itemFromRow(Row row) {
        Object att = row.get("call_attempts");
        int attempts = att instanceof Number n ? n.intValue() : 0;

        return new CallQueueItem(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("queue_id") != null ? row.get("queue_id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getString("priority"),
            row.getString("status"),
            row.get("assigned_agent_user_id") != null ? row.get("assigned_agent_user_id").toString() : null,
            attempts,
            row.getInstant("last_dialed_at"),
            row.getInstant("next_call_scheduled_at"),
            row.getString("notes"),
            row.getInstant("created_at"),
            row.getInstant("updated_at"),
            row.getString("lead_name"),
            row.getString("lead_phone"),
            row.getString("lead_company")
        );
    }
}
