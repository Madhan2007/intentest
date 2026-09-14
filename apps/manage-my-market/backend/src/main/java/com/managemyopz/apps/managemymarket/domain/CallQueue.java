/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a telemarketing call queue.
 */
public record CallQueue(
    String id,
    String companyId,
    String queueName,
    String description,
    int priority,
    String status,
    String createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {}
