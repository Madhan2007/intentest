/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Catalog application joined with one company's license state.
 */
package com.managemyopz.modules.apps.domain;

/** One launcher/dashboard application for the signed-in company. */
public record CompanyApplicationView(
    String id,
    String companyApplicationId,
    String appKey,
    String productCode,
    String category,
    String name,
    String description,
    String iconKey,
    String iconBackgroundColor,
    int sortOrder,
    String licenseState,
    boolean favourite
) {}
