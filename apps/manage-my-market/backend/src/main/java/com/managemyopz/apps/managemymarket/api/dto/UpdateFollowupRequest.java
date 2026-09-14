/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.time.Instant;

/**
 * Request payload for updating a follow-up.
 */
public record UpdateFollowupRequest(
    String id,
    String reason,
    Instant scheduledAt,
    String assignedAgentUserId,
    String priority,
    String status,
    String notes
) {}
