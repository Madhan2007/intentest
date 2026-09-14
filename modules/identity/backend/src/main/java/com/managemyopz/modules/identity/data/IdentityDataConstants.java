/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Named data commands and row keys for the identity module.
 */
package com.managemyopz.modules.identity.data;

/** Defines identity data-access command and parameter names. */
public final class IdentityDataConstants {

    /** Named database (platform.yaml `databases:` block) identity's tables live in.
     *  Table creation/reconciliation is schema-YAML-driven (modules/identity/db/schema/id_user.yaml) —
     *  this constant is only for routing the hand-written login/query commands below. */
    public static final String DATABASE_NAME = "OPZUSER";

    /** Named database (platform.yaml `databases:` block) the platform_admin
     *  table lives in — the central company/license registry, not per-company. */
    public static final String PLATFORM_ADMIN_DATABASE_NAME = "OPZMAIN";
    public static final String COMPANY_ENTITY = "company_information";
    public static final String COMPANY_REFERENCES_COLUMN = "company_references";
    public static final String COUNT_USERS_COMMAND = "identity.count_users";
    public static final String CREATE_USER_COMMAND = "identity.create_user";
    public static final String FIND_USER_BY_USERNAME_COMMAND = "identity.find_user_by_username";
    public static final String FIND_USER_BY_EMAIL_COMMAND = "identity.find_user_by_email";
    public static final String RESTORE_ADMIN_USERNAME_COMMAND = "identity.restore_admin_username";
    public static final String ASSIGN_ADMIN_EMAIL_COMMAND = "identity.assign_admin_email";

    /** platform_admin (OPZMAIN) named commands — doc 35 §2. */
    public static final String PLATFORM_ADMIN_FIND_BY_USERNAME_COMMAND = "identity.platform_admin.find_by_username";
    public static final String PLATFORM_ADMIN_COUNT_COMMAND = "identity.platform_admin.count";
    public static final String PLATFORM_ADMIN_CREATE_COMMAND = "identity.platform_admin.create";
    public static final String PLATFORM_ADMIN_COUNT_KEY = "admin_count";

    public static final String USER_COUNT_KEY = "user_count";
    public static final String USER_ID_KEY = "id";
    public static final String USERNAME_KEY = "username";
    public static final String EMAIL_KEY = "email";
    public static final String COMPANY_ID_KEY = "company_id";
    public static final String DISPLAY_NAME_KEY = "display_name";
    public static final String PASSWORD_HASH_KEY = "password_hash";
    public static final String ROLES_KEY = "roles";
    public static final String ROLE_KEY = "role";
    public static final String ENABLED_KEY = "enabled";

    private IdentityDataConstants() {
    }
}
