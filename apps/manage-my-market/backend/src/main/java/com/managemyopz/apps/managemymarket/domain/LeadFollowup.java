/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a scheduled follow-up against a lead.
 */
public record LeadFollowup(
    String id,
    String leadId,
    String reason,
    Instant scheduledAt,
    String assignedAgentUserId,
    String priority,
    String status,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {}
