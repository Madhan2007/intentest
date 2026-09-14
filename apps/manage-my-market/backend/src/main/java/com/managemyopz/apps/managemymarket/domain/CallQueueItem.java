/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a lead item in a call queue.
 */
public record CallQueueItem(
    String id,
    String queueId,
    String leadId,
    String priority,
    String status,
    String assignedAgentUserId,
    int callAttempts,
    Instant lastDialedAt,
    Instant nextCallScheduledAt,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    String leadName,
    String leadPhone,
    String leadCompany
) {}
