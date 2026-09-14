/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.server.MemoryCommandRegistry;
import com.managemyopz.modules.identity.domain.User;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers memory-mode identity queries.
 *
 * <p>Users are deliberately not seeded here. Passwords and user identifiers
 * must be created through the identity-management flow and stored in the
 * configured data store.</p>
 */
public class IdentityMemoryStore {

    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    /**
     * Registers the queries used to locate an in-memory identity record.
     *
     * @param registry memory command registry that owns the query
     */
    public IdentityMemoryStore(MemoryCommandRegistry registry) {
        registry.registerQuery(IdentityDataConstants.FIND_USER_BY_USERNAME_COMMAND, params -> {
            String username = String.valueOf(params.get(IdentityDataConstants.USERNAME_KEY));
            return rowList(usersByUsername.get(username));
        });
        registry.registerQuery(IdentityDataConstants.FIND_USER_BY_EMAIL_COMMAND, params -> {
            String email = String.valueOf(params.get(IdentityDataConstants.EMAIL_KEY));
            return rowList(findByEmail(email));
        });
    }

    /**
     * Finds an in-memory account by stored email.
     *
     * @param email stored email
     * @return matching user, or null when no email matches
     */
    private User findByEmail(String email) {
        for (User user : usersByUsername.values()) {
            if (email.equals(user.email())) {
                return user;
            }
        }
        return null;
    }

    /**
     * Converts a stored user into a one-row query result.
     *
     * @param user located user, or null when no row matched
     * @return a single mapped row, or an empty list
     */
    private static List<Row> rowList(User user) {
        if (user == null) {
            return List.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(IdentityDataConstants.USER_ID_KEY, user.id());
        values.put(IdentityDataConstants.USERNAME_KEY, user.username());
        values.put(IdentityDataConstants.EMAIL_KEY, user.email());
        values.put(IdentityDataConstants.DISPLAY_NAME_KEY, user.displayName());
        values.put(IdentityDataConstants.PASSWORD_HASH_KEY, user.passwordHash());
        values.put(IdentityDataConstants.ROLES_KEY, user.roles());
        values.put(IdentityDataConstants.ENABLED_KEY, user.enabled());
        values.put(IdentityDataConstants.COMPANY_ID_KEY, user.companyId());
        return List.of(new Row(values));
    }
}
