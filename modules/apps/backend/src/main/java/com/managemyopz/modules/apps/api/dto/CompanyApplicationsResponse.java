/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Envelope payload for the company application catalog.
 */
package com.managemyopz.modules.apps.api.dto;

import com.managemyopz.modules.apps.domain.CompanyApplicationView;

import java.util.List;

/** All catalog applications with the signed-in company's license state. */
public record CompanyApplicationsResponse(List<CompanyApplicationView> applications) {}
