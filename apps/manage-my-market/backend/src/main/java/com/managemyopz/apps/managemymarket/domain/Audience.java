/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing an audience segment.
 */
public record Audience(
    String id,
    String companyId,
    String audienceName,
    String description,
    String rulesJson,
    Instant createdAt,
    Instant updatedAt
) {}
