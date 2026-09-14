/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallQueue;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Call queue operations.
 */
public class DataClientCallQueueRepository implements CallQueueRepository {

    private final DataClient dataClient;

    public DataClientCallQueueRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(CallQueue queue) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", queue.id());
        params.put("company_id", queue.companyId());
        params.put("queue_name", queue.queueName());
        params.put("description", queue.description());
        params.put("priority", queue.priority());
        params.put("status", queue.status());
        params.put("created_by_user_id", queue.createdByUserId());

        dataClient.execute(MarketDataConstants.CALL_QUEUE_INSERT, params);
    }

    @Override
    public List<CallQueue> listByCompany(String companyId) {
        return dataClient.query(MarketDataConstants.CALL_QUEUE_LIST_BY_COMPANY, Map.of("company_id", companyId)).stream()
            .map(DataClientCallQueueRepository::queueFromRow)
            .toList();
    }

    @Override
    public Optional<CallQueue> findById(String id, String companyId) {
        return listByCompany(companyId).stream().filter(q -> q.id().equals(id)).findFirst();
    }

    private static CallQueue queueFromRow(Row row) {
        Object prio = row.get("priority");
        int priority = prio instanceof Number n ? n.intValue() : 0;

        return new CallQueue(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("queue_name"),
            row.getString("description"),
            priority,
            row.getString("status"),
            row.get("created_by_user_id") != null ? row.get("created_by_user_id").toString() : null,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
