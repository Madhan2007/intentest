/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a recipient lead enrolled in a campaign.
 */
public record CampaignRecipient(
    String id,
    String campaignId,
    String leadId,
    String status,
    Instant sentAt,
    Instant updatedAt,
    String leadName,
    String leadEmail,
    String leadPhone
) {}
