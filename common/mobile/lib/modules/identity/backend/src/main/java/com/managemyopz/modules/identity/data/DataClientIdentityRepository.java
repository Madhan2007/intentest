/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.modules.identity.domain.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** db.type=postgres path — reads via the named command, never raw JDBC (doc 04 §11). */
public class DataClientIdentityRepository implements IdentityRepository {

    private final DataClient dataClient;

    public DataClientIdentityRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<User> findByUsername(String username) {
        return dataClient.queryOne("identity.find_user_by_username", Map.of("username", username))
            .map(row -> new User(
                row.get("id").toString(),
                row.getString("username"),
                row.getString("display_name"),
                row.getString("password_hash"),
                row.get("roles") instanceof List<?> l ? (List<String>) l : List.of(),
                Boolean.TRUE.equals(row.getBoolean("enabled"))
            ));
    }

    @Override
    public void ensureUserTable() {
        dataClient.execute(IdentityDataConstants.ENSURE_USER_TABLE_COMMAND, Map.of());
    }

    @Override
    public long countUsers() {
        return dataClient.queryOne(IdentityDataConstants.COUNT_USERS_COMMAND, Map.of())
            .map(row -> row.getLong(IdentityDataConstants.USER_COUNT_KEY))
            .orElse(0L);
    }

    @Override
    public int createIfAbsent(User user) {
        return dataClient.execute(
            IdentityDataConstants.CREATE_USER_COMMAND,
            Map.of(
                IdentityDataConstants.USER_ID_KEY, user.id(),
                IdentityDataConstants.USERNAME_KEY, user.username(),
                IdentityDataConstants.DISPLAY_NAME_KEY, user.displayName(),
                IdentityDataConstants.PASSWORD_HASH_KEY, user.passwordHash(),
                IdentityDataConstants.ROLE_KEY, user.roles().getFirst(),
                IdentityDataConstants.ENABLED_KEY, user.enabled()
            )
        );
    }
}
