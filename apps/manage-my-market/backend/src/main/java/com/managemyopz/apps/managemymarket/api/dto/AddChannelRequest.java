/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.math.BigDecimal;

/**
 * Request payload for adding a channel to a campaign.
 */
public record AddChannelRequest(
    String channelType,
    BigDecimal budget,
    BigDecimal spend
) {}
