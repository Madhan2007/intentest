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
 * Immutable domain record representing a referral partner.
 */
public record Referrer(
    String id,
    String companyId,
    String referrerName,
    String email,
    String phone,
    String status,
    int leadsCount,
    BigDecimal rewardsPaidTotal,
    BigDecimal rewardsPendingTotal,
    LocalDate joinedAt,
    Instant createdAt,
    Instant updatedAt
) {}
