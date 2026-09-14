/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.time.Instant;

/**
 * Request payload for creating a call log.
 */
public record CreateCallLogRequest(
    String queueId,
    String leadId,
    String phoneNumber,
    String connectionStatus,
    String disposition,
    Integer durationSeconds,
    String recordingUrl,
    String notes,
    Instant scheduledFollowupAt
) {}
