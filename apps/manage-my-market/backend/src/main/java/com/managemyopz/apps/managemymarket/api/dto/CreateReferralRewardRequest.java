/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.math.BigDecimal;

/**
 * Request payload for creating a referral reward.
 */
public record CreateReferralRewardRequest(
    String leadId,
    BigDecimal amount,
    String payoutDetails
) {}
