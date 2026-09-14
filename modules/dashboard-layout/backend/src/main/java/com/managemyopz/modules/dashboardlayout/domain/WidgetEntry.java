/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.domain;

import java.util.Map;

public record WidgetEntry(
    String widgetKey,
    int x,
    int y,
    int w,
    int h,
    Map<String, Object> props
) {}
