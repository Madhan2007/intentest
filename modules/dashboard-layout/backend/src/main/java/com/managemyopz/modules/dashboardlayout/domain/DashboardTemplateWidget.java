/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.domain;

import java.util.UUID;

public record DashboardTemplateWidget(
    UUID id,
    String templateKey,
    String widgetKey,
    String zoneKey,
    String positionJson,
    String widgetPropsJson
) {}
