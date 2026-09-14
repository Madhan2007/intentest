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
import org.springframework.security.crypto.password.PasswordEncoder;

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
     * Registers the query used to locate an in-memory identity record.
     *
     * @param registry memory command registry that owns the query
     */
    public IdentityMemoryStore(MemoryCommandRegistry registry) {
        registry.registerQuery("identity.find_user_by_username", params -> {
            String username = String.valueOf(params.get("username"));
            User user = usersByUsername.get(username);
            if (user == null) return List.of();
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("id", user.id());
            values.put("username", user.username());
            values.put("display_name", user.displayName());
            values.put("password_hash", user.passwordHash());
            values.put("roles", user.roles());
            values.put("enabled", user.enabled());
            return List.of(new Row(values));
        });
    }
}
