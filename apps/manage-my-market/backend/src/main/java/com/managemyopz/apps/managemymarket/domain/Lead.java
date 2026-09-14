/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing a marketing lead / prospect.
 */
public record Lead(
    String id,
    String companyId,
    String leadCode,
    String leadSourceType,
    String firstName,
    String lastName,
    String displayName,
    String companyName,
    String email,
    String phone,
    String status,
    String sourceDetail,
    Integer estimatedValue,
    String priority,
    Integer intentScore,
    String referrerId,
    String ownerUserId,
    String createdByUserId,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {}
