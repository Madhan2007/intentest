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
 * Immutable domain record representing a channel budget allocation for a campaign.
 */
public record CampaignChannel(
    String id,
    String campaignId,
    String channelType,
    BigDecimal budget,
    BigDecimal spend,
    Instant createdAt
) {}
