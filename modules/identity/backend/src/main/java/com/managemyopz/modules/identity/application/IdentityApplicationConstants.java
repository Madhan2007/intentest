/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Constants for identity application services.
 */
package com.managemyopz.modules.identity.application;

/**
 * Defines non-secret constants used by identity application services.
 */
public final class IdentityApplicationConstants {

    /** Role that grants full administrative access. */
    public static final String ROLE_ADMIN = "admin";

    /** Username stored in id_user for the bootstrap administrator. */
    public static final String ADMIN_USERNAME = "admin";

    /** Email stored in id_user for the bootstrap administrator. */
    public static final String ADMIN_EMAIL = "admin@technosprint.net";

    /** Separator between a company reference and the stored username. */
    public static final String COMPANY_USERNAME_SEPARATOR = "/";

    /** Role that grants elevated access. */
    public static final String ROLE_SUPERUSER = "superuser";

    /** Role that grants baseline access. */
    public static final String ROLE_USER = "user";

    /** Role for a central/platform administrator with no company_id — doc 35 §2.
     *  Triggered by typing a bare username (no "/" and no "@") on the login field. */
    public static final String ROLE_PLATFORM_ADMIN = "platform_admin";

    /** Identifier of the optional administration module. */
    public static final String ADMIN_MODULE_ID = "admin";

    /** Identifier of the company-setup module — the only module a
     *  platform admin session's matrix ever grants (doc 35 §2). */
    public static final String COMPANY_SETUP_MODULE_ID = "company-setup";

    /** Wildcard app or feature identifier in the compact access matrix. */
    public static final String WILDCARD = "*";

    /** Full compact access grant. */
    public static final String FULL_ACCESS = "vcua";

    /** View-only compact access grant. */
    public static final String VIEW_ACCESS = "v";

    /** Prefix for opaque session entries in the cache. */
    public static final String SESSION_CACHE_KEY_PREFIX = "session:";

    private IdentityApplicationConstants() {
    }
}
