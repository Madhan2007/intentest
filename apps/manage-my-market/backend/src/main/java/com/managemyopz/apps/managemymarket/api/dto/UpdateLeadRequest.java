/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

/**
 * Request payload for updating a lead.
 * Note: ownerUserId is purposefully excluded; reassignments must go through assign endpoint.
 */
public record UpdateLeadRequest(
    String id,
    String displayName,
    String firstName,
    String lastName,
    String companyName,
    String email,
    String phone,
    String status,
    String sourceDetail,
    Integer estimatedValue,
    String priority,
    String referrerId,
    String notes
) {}
