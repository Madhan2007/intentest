/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a completed telemarketing call.
 */
public record CallLog(
    String id,
    String companyId,
    String queueId,
    String leadId,
    String agentUserId,
    String phoneNumber,
    String connectionStatus,
    String disposition,
    int durationSeconds,
    String recordingUrl,
    String notes,
    Instant scheduledFollowupAt,
    Instant createdAt
) {}
