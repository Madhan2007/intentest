/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.CallLogRepository;
import com.managemyopz.apps.managemymarket.data.CallQueueItemRepository;
import com.managemyopz.apps.managemymarket.data.CallQueueRepository;
import com.managemyopz.apps.managemymarket.domain.CallLog;
import com.managemyopz.apps.managemymarket.domain.CallQueue;
import com.managemyopz.apps.managemymarket.domain.CallQueueItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for telemarketing dialer queues and call log recording.
 */
public class CallQueueService {

    private final CallQueueRepository queueRepository;
    private final CallQueueItemRepository itemRepository;
    private final CallLogRepository callLogRepository;
    private final LeadActivityService activityService;

    public CallQueueService(CallQueueRepository queueRepository,
                            CallQueueItemRepository itemRepository,
                            CallLogRepository callLogRepository,
                            LeadActivityService activityService) {
        this.queueRepository = queueRepository;
        this.itemRepository = itemRepository;
        this.callLogRepository = callLogRepository;
        this.activityService = activityService;
    }

    public CallQueue createQueue(String companyId, String queueName, String description, int priority, String createdByUserId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("queue_name is required");
        }

        String id = UUID.randomUUID().toString();
        CallQueue queue = new CallQueue(
            id,
            companyId,
            queueName.trim(),
            description,
            priority,
            ManageMyMarketConstants.CALL_QUEUE_STATUS_ACTIVE,
            createdByUserId,
            Instant.now(),
            Instant.now()
        );
        queueRepository.insert(queue);
        return queue;
    }

    public List<CallQueue> listQueues(String companyId) {
        return queueRepository.listByCompany(companyId);
    }

    public Optional<CallQueue> getQueueById(String id, String companyId) {
        return queueRepository.findById(id, companyId);
    }

    public CallQueueItem addItem(String queueId, String leadId, String priority, String assignedAgentUserId, Instant nextCallScheduledAt, String notes) {
        String id = UUID.randomUUID().toString();
        CallQueueItem item = new CallQueueItem(
            id,
            queueId,
            leadId,
            priority != null ? priority : ManageMyMarketConstants.PRIORITY_WARM,
            ManageMyMarketConstants.CALL_ITEM_STATUS_PENDING,
            assignedAgentUserId,
            0,
            null,
            nextCallScheduledAt,
            notes,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null
        );
        itemRepository.insert(item);
        return item;
    }

    public void updateItem(String id, String priority, String status, String assignedAgentUserId, Instant nextCallScheduledAt, String notes) {
        itemRepository.update(id, priority, status, assignedAgentUserId, nextCallScheduledAt, notes);
    }

    public List<CallQueueItem> listItems(String queueId, String status, String assignedAgentUserId) {
        return itemRepository.listByQueue(queueId, status, assignedAgentUserId);
    }

    /**
     * Enforces the business rule: call_attempts increments ONLY on real call log insert.
     */
    public CallLog recordCall(String companyId, String queueId, String leadId, String agentUserId,
                              String phoneNumber, String connectionStatus, String disposition,
                              int durationSeconds, String recordingUrl, String notes,
                              Instant scheduledFollowupAt) {
        String callLogId = UUID.randomUUID().toString();
        CallLog callLog = new CallLog(
            callLogId,
            companyId,
            queueId,
            leadId,
            agentUserId,
            phoneNumber,
            connectionStatus != null ? connectionStatus : ManageMyMarketConstants.CALL_CONNECTION_CONNECTED,
            disposition,
            durationSeconds,
            recordingUrl,
            notes,
            scheduledFollowupAt,
            Instant.now()
        );

        // 1. Insert call log
        callLogRepository.insert(callLog);

        // 2. Increment call attempts on queue item if queueId and leadId are present
        if (queueId != null && leadId != null) {
            itemRepository.incrementAttempts(queueId, leadId);
        }

        // 3. Record timeline activity on lead
        if (leadId != null) {
            activityService.logActivity(
                leadId,
                ManageMyMarketConstants.ACTIVITY_TYPE_CALL,
                "Call Logged: " + (disposition != null ? disposition : connectionStatus),
                notes != null ? notes : "Duration: " + durationSeconds + "s",
                null,
                connectionStatus,
                callLogId,
                null,
                agentUserId
            );
        }

        return callLog;
    }

    public List<CallLog> listCallsByLead(String leadId) {
        return callLogRepository.listByLead(leadId);
    }
}
