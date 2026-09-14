/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

/**
 * Request payload for updating a referrer.
 * Aggregate fields (leadsCount, rewardsPaidTotal, etc.) are excluded per architecture rules.
 */
public record UpdateReferrerRequest(
    String id,
    String referrerName,
    String email,
    String phone,
    String status
) {}
