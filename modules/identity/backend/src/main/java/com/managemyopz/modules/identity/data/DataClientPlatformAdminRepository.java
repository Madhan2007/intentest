/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-13
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.modules.identity.domain.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** db.type=postgres path — reads via the named command, never raw JDBC (doc 04 §11).
 *  Bound to OPZMAIN, not OPZUSER — platform admins carry no company_id. */
public class DataClientPlatformAdminRepository implements PlatformAdminRepository {

    private final DataClient dataClient;

    /**
     * Creates the PostgreSQL platform admin repository.
     *
     * @param dataClient kernel data client bound to OPZMAIN
     */
    public DataClientPlatformAdminRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return dataClient.queryOne(
            IdentityDataConstants.PLATFORM_ADMIN_FIND_BY_USERNAME_COMMAND,
            Map.of(IdentityDataConstants.USERNAME_KEY, username)
        ).map(DataClientPlatformAdminRepository::userFromRow);
    }

    @Override
    public long countAdmins() {
        return dataClient.queryOne(IdentityDataConstants.PLATFORM_ADMIN_COUNT_COMMAND, Map.of())
            .map(row -> row.getLong(IdentityDataConstants.PLATFORM_ADMIN_COUNT_KEY))
            .orElse(0L);
    }

    @Override
    public int createIfAbsent(User admin) {
        Map<String, Object> params = new HashMap<>();
        params.put(IdentityDataConstants.USER_ID_KEY, admin.id());
        params.put(IdentityDataConstants.USERNAME_KEY, admin.username());
        params.put(IdentityDataConstants.DISPLAY_NAME_KEY, admin.displayName());
        params.put(IdentityDataConstants.PASSWORD_HASH_KEY, admin.passwordHash());
        params.put(IdentityDataConstants.ENABLED_KEY, admin.enabled());
        return dataClient.execute(IdentityDataConstants.PLATFORM_ADMIN_CREATE_COMMAND, params);
    }

    /**
     * Maps a platform_admin row into the shared {@link User} value object.
     * Email is always blank and companyId is always null — a platform admin
     * is never routed to a company database (doc 35 §2).
     *
     * @param row platform_admin query row
     * @return user mapped from the row
     */
    private static User userFromRow(Row row) {
        return new User(
            row.get(IdentityDataConstants.USER_ID_KEY).toString(),
            row.getString(IdentityDataConstants.USERNAME_KEY),
            "",
            row.getString(IdentityDataConstants.DISPLAY_NAME_KEY),
            row.getString(IdentityDataConstants.PASSWORD_HASH_KEY),
            List.of(com.managemyopz.modules.identity.application.IdentityApplicationConstants.ROLE_PLATFORM_ADMIN),
            Boolean.TRUE.equals(row.getBoolean(IdentityDataConstants.ENABLED_KEY)),
            null
        );
    }
}
