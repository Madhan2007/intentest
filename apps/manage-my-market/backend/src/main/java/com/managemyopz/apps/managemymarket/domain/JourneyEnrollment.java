/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a lead enrollment in a marketing journey.
 */
public record JourneyEnrollment(
    String id,
    String journeyId,
    String leadId,
    String currentNodeId,
    String status,
    Instant enrolledAt,
    Instant updatedAt,
    String leadName,
    String leadEmail
) {}
