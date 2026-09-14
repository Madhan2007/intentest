/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallQueueItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Call queue items.
 */
public interface CallQueueItemRepository {

    void insert(CallQueueItem item);

    void update(String id, String priority, String status, String assignedAgentUserId, Instant nextCallScheduledAt, String notes);

    List<CallQueueItem> listByQueue(String queueId, String status, String assignedAgentUserId);

    Optional<CallQueueItem> findById(String id);

    void incrementAttempts(String queueId, String leadId);
}
