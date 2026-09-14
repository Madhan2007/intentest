/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.domain;

import java.time.Instant;
import java.util.UUID;

public record DashboardTemplate(
    String templateKey,
    String appKey,
    String templateName,
    String description,
    String thumbnailUri,
    boolean isBuiltin,
    UUID createdBy,
    Instant createdAt
) {}
