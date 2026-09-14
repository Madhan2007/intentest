/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing lead ownership assignment history.
 */
public record LeadAssignment(
    String id,
    String leadId,
    String assignedByUserId,
    String assignedToUserId,
    String transferReason,
    Instant assignedAt
) {}
