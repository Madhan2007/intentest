/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Request body for licensing or installing one catalog application.
 */
package com.managemyopz.modules.apps.api.dto;

/** Identifies the catalog application to license or install. */
public record ApplicationActionRequest(String applicationId) {}
