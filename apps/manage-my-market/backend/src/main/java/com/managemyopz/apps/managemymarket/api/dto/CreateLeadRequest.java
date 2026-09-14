/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

/**
 * Request payload for creating a new lead.
 */
public record CreateLeadRequest(
    String displayName,
    String leadSourceType,
    String firstName,
    String lastName,
    String companyName,
    String email,
    String phone,
    String sourceDetail,
    Integer estimatedValue,
    String priority,
    String referrerId,
    String ownerUserId,
    String notes
) {}
