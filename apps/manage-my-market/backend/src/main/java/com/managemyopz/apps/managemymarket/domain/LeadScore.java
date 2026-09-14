/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing engagement/quality score for a lead.
 */
public record LeadScore(
    String leadId,
    int score,
    String breakdownJson,
    Instant updatedAt
) {}
