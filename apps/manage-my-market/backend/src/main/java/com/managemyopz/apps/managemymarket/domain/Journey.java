/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a marketing journey / workflow definition.
 */
public record Journey(
    String id,
    String companyId,
    String journeyName,
    String status,
    String triggerType,
    String flowDefinitionJson,
    Instant createdAt,
    Instant updatedAt
) {}
