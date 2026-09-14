/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing an agent marketing profile.
 */
public record AgentProfile(
    String agentUserId,
    String companyId,
    String marketingType,
    String linkedPersonId,
    Instant createdAt,
    Instant updatedAt
) {}
