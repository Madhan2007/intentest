/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.domain;

import java.time.Instant;

/**
 * Immutable domain record representing an email template.
 */
public record EmailTemplate(
    String id,
    String companyId,
    String templateName,
    String subject,
    String bodyHtml,
    String bodyText,
    Instant createdAt,
    Instant updatedAt
) {}
