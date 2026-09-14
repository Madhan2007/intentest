/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable domain record representing a referral reward payout.
 */
public record ReferralReward(
    String id,
    String referrerId,
    String leadId,
    BigDecimal amount,
    String status,
    String payoutDetails,
    Instant createdAt,
    Instant updatedAt
) {}
