/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

/**
 * Request payload for POST /leads/read (pagination & filtering or single lookup).
 */
public record ReadLeadsRequest(
    String id,
    String leadCode,
    String status,
    String leadSourceType,
    String ownerUserId,
    Integer limit,
    Integer offset
) {}
