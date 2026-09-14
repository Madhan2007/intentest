/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Request body for marking a company application as favourite.
 */
package com.managemyopz.modules.apps.api.dto;

/** Identifies the catalog application and the requested favourite value. */
public record ApplicationFavouriteRequest(String applicationId, Boolean favourite) {}
