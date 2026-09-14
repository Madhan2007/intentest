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

    /** Role that grants elevated access. */
    public static final String ROLE_SUPERUSER = "superuser";

    /** Role that grants baseline access. */
    public static final String ROLE_USER = "user";

    /** Identifier of the optional administration module. */
    public static final String ADMIN_MODULE_ID = "admin";

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