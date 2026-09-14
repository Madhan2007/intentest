/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.domain;

import java.time.Instant;
import java.util.List;

public record WidgetCatalogItem(
    String widgetKey,
    String appKey,
    String widgetName,
    String description,
    String iconKey,
    String defaultZone,
    int defaultW,
    int defaultH,
    int minW,
    int minH,
    String propsSchema,
    List<String> allowedRoles,
    boolean isSystem,
    String status,
    Instant createdAt
) {}
