/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for updating a campaign.
 */
public record UpdateCampaignRequest(
    String id,
    String campaignName,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal budget
) {}
