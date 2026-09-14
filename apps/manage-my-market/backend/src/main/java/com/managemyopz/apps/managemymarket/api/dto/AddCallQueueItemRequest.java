/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.time.Instant;

/**
 * Request payload for adding a lead to a call queue.
 */
public record AddCallQueueItemRequest(
    String leadId,
    String priority,
    String assignedAgentUserId,
    Instant nextCallScheduledAt,
    String notes
) {}
