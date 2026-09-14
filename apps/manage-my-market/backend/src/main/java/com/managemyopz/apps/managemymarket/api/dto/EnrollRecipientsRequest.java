/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.util.List;

/**
 * Request payload for enrolling leads into a campaign.
 */
public record EnrollRecipientsRequest(
    List<String> leadIds
) {}
