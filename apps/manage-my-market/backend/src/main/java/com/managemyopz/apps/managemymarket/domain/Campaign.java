/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable domain record representing a marketing campaign.
 */
public record Campaign(
    String id,
    String companyId,
    String campaignName,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal budget,
    String createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {}
