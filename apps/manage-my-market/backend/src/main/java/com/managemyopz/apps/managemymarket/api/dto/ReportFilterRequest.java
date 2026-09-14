/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.api.dto;

import java.time.Instant;

public record ReportFilterRequest(
    Instant fromDate,
    Instant toDate,
    String status,
    String agentUserId,
    String journeyId,
    Integer limit
) {}
