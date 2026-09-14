/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

/**
 * Request payload for disqualifying a lead.
 */
public record DisqualifyLeadRequest(
    String id,
    String reason
) {}
