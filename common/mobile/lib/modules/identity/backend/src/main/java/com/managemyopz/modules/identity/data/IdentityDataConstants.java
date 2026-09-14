/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Named data commands and row keys for the identity module.
 */
package com.managemyopz.modules.identity.data;

/** Defines identity data-access command and parameter names. */
public final class IdentityDataConstants {

    public static final String ENSURE_USER_TABLE_COMMAND = "identity.ensure_user_table";
    public static final String COUNT_USERS_COMMAND = "identity.count_users";
    public static final String CREATE_USER_COMMAND = "identity.create_user";
    public static final String USER_COUNT_KEY = "user_count";
    public static final String USER_ID_KEY = "id";
    public static final String USERNAME_KEY = "username";
    public static final String DISPLAY_NAME_KEY = "display_name";
    public static final String PASSWORD_HASH_KEY = "password_hash";
    public static final String ROLES_KEY = "roles";
    public static final String ROLE_KEY = "role";
    public static final String ENABLED_KEY = "enabled";

    private IdentityDataConstants() {
    }
}