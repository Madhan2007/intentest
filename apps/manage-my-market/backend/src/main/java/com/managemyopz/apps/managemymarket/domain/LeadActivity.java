/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing an activity/event on a lead timeline.
 */
public record LeadActivity(
    String id,
    String leadId,
    String activityType,
    String title,
    String description,
    String oldValue,
    String newValue,
    String relatedCallLogId,
    String relatedFollowupId,
    String performedByUserId,
    Instant createdAt
) {}
