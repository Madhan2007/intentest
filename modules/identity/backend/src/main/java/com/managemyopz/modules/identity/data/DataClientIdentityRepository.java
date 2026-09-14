/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.modules.identity.domain.User;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** db.type=postgres path — reads via the named command, never raw JDBC (doc 04 §11). */
public class DataClientIdentityRepository implements IdentityRepository {

    private final DataClient dataClient;
    private final SchemaRegistry schemaRegistry;

    /**
     * Creates the PostgreSQL identity repository.
     *
     * @param dataClient kernel data client bound to the identity commands
     * @param schemaRegistry YAML entity schemas used for company_id updates
     */
    public DataClientIdentityRepository(DataClient dataClient, SchemaRegistry schemaRegistry) {
        this.dataClient = dataClient;
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * Finds a user by stored username within a company.
     *
     * @param username stored username
     * @param companyId company_information.id stored on the user
     * @return matching user when one row exists
     */
    @Override
    public Optional<User> findByUsernameAndCompanyId(String username, String companyId) {
        if (companyId == null || companyId.isBlank()) {
            return Optional.empty();
        }
        return findByUsername(username).filter(user -> companyId.equals(user.companyId()));
    }

    /**
     * Finds a user by stored username.
     *
     * @param username stored username
     * @return matching user when one row exists
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return dataClient.queryOne(
            IdentityDataConstants.FIND_USER_BY_USERNAME_COMMAND,
            Map.of(IdentityDataConstants.USERNAME_KEY, username)
        )
            .map(DataClientIdentityRepository::userFromRow);
    }

    /**
     * Finds a user by stored email.
     *
     * @param email stored email
     * @return matching user when one row exists
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return dataClient.queryOne(
            IdentityDataConstants.FIND_USER_BY_EMAIL_COMMAND,
            Map.of(IdentityDataConstants.EMAIL_KEY, email)
        )
            .map(DataClientIdentityRepository::userFromRow);
    }

    /**
     * Counts stored identity accounts.
     *
     * @return number of rows in the identity table
     */
    @Override
    public long countUsers() {
        return dataClient.queryOne(IdentityDataConstants.COUNT_USERS_COMMAND, Map.of())
            .map(row -> row.getLong(IdentityDataConstants.USER_COUNT_KEY))
            .orElse(0L);
    }

    /**
     * Inserts a user when the username is not already claimed.
     *
     * @param user account to insert
     * @return number of rows inserted
     */
    @Override
    public int createIfAbsent(User user) {
        Map<String, Object> params = new HashMap<>();
        params.put(IdentityDataConstants.USER_ID_KEY, user.id());
        params.put(IdentityDataConstants.USERNAME_KEY, user.username());
        params.put(IdentityDataConstants.EMAIL_KEY, user.email());
        params.put(IdentityDataConstants.DISPLAY_NAME_KEY, user.displayName());
        params.put(IdentityDataConstants.PASSWORD_HASH_KEY, user.passwordHash());
        params.put(IdentityDataConstants.ROLE_KEY, user.roles().getFirst());
        params.put(IdentityDataConstants.ENABLED_KEY, user.enabled());
        params.put(IdentityDataConstants.COMPANY_ID_KEY, user.companyId());
        return dataClient.execute(IdentityDataConstants.CREATE_USER_COMMAND, params);
    }

    /**
     * Moves a legacy email-as-username administrator onto the current username
     * and email columns.
     *
     * @param username username to restore
     * @param email email currently stored as the username
     * @return number of rows updated
     */
    @Override
    public int restoreAdminUsername(String username, String email) {
        return dataClient.execute(
            IdentityDataConstants.RESTORE_ADMIN_USERNAME_COMMAND,
            Map.of(
                IdentityDataConstants.USERNAME_KEY, username,
                IdentityDataConstants.EMAIL_KEY, email
            )
        );
    }

    /**
     * Assigns the bootstrap administrator email when that username exists.
     *
     * @param username administrator username
     * @param email administrator email
     * @return number of rows updated
     */
    @Override
    public int assignAdminEmail(String username, String email) {
        return dataClient.execute(
            IdentityDataConstants.ASSIGN_ADMIN_EMAIL_COMMAND,
            Map.of(
                IdentityDataConstants.USERNAME_KEY, username,
                IdentityDataConstants.EMAIL_KEY, email
            )
        );
    }

    /**
     * Assigns the administrator company when that username exists.
     *
     * @param username administrator username
     * @param companyId company_information.id
     * @return number of rows updated
     */
    @Override
    public int assignAdminCompany(String username, String companyId) {
        Optional<User> user = findByUsername(username);
        if (user.isEmpty() || companyId == null || companyId.equals(user.get().companyId())) {
            return 0;
        }
        Optional<EntitySchema> schema = schemaRegistry.find("id_user");
        if (schema.isEmpty()) {
            return 0;
        }
        UUID userId;
        UUID linkedCompanyId;
        try {
            userId = UUID.fromString(user.get().id());
            linkedCompanyId = UUID.fromString(companyId);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
        SqlCommand command = GenericSqlBuilder.update(
            schema.get(),
            userId,
            Map.of(IdentityDataConstants.COMPANY_ID_KEY, linkedCompanyId)
        );
        return dataClient.queryRaw(command.sql(), command.params()).isEmpty() ? 0 : 1;
    }

    /**
     * Maps a data-client identity row into a user value.
     *
     * @param row identity query row
     * @return user mapped from the row
     */
    private static User userFromRow(Row row) {
        return new User(
            row.get(IdentityDataConstants.USER_ID_KEY).toString(),
            row.getString(IdentityDataConstants.USERNAME_KEY),
            row.getString(IdentityDataConstants.EMAIL_KEY),
            row.getString(IdentityDataConstants.DISPLAY_NAME_KEY),
            row.getString(IdentityDataConstants.PASSWORD_HASH_KEY),
            rolesFrom(row.get(IdentityDataConstants.ROLES_KEY)),
            Boolean.TRUE.equals(row.getBoolean(IdentityDataConstants.ENABLED_KEY)),
            companyIdFrom(row.get(IdentityDataConstants.COMPANY_ID_KEY))
        );
    }

    /**
     * Reads a company UUID column into a string identifier.
     *
     * @param companyIdValue raw company_id column
     * @return company id, or null when the column is empty
     */
    private static String companyIdFrom(Object companyIdValue) {
        return companyIdValue == null ? null : companyIdValue.toString();
    }

    /**
     * Converts a Postgres text[] / JDBC array / list value into role names.
     *
     * @param rolesValue raw column value from the identity query
     * @return role names, or an empty list when the column is absent
     */
    private static List<String> rolesFrom(Object rolesValue) {
        if (rolesValue == null) {
            return List.of();
        }
        if (rolesValue instanceof List<?> list) {
            List<String> roles = new ArrayList<>();
            for (Object role : list) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
            return List.copyOf(roles);
        }
        if (rolesValue instanceof String[] array) {
            return List.of(array);
        }
        if (rolesValue instanceof Object[] array) {
            List<String> roles = new ArrayList<>();
            for (Object role : array) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
            return List.copyOf(roles);
        }
        if (rolesValue instanceof Array jdbcArray) {
            try {
                return rolesFrom(jdbcArray.getArray());
            } catch (SQLException exception) {
                return List.of();
            }
        }
        return List.of();
    }
}
