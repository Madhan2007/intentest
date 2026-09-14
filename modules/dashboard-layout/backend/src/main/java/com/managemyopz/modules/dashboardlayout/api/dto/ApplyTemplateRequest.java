/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.api.dto;

public record ApplyTemplateRequest(
    String appKey,
    String roleKey,
    String templateKey
) {}
