/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.server;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.Isolation;
import com.managemyopz.kernel.data.client.Row;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * db.type=postgres — production DataServer (doc 07 §4.1). Bind parameters
 * only; statement names resolve to SQL text via {@link CommandCatalog}, never
 * string-concatenated in feature code.
 */
public class PostgresDataServer implements DataClient, AutoCloseable {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate readCommittedTx;
    private final TransactionTemplate serializableTx;
    private final CommandCatalog commandCatalog;
    private final HikariDataSource dataSource;

    /**
     * Creates a Postgres-backed data client from resolved platform properties.
     *
     * @param postgresConfig platform db.postgres configuration values
     * @param commandCatalog catalog that maps statement names to SQL text
     */
    public PostgresDataServer(Map<String, Object> postgresConfig, CommandCatalog commandCatalog) {
        this.commandCatalog = commandCatalog;
        HikariConfig hikari = new HikariConfig();
        String host = stringValue(
            postgresConfig,
            DataServerConstants.POSTGRES_HOST_KEY,
            DataServerConstants.DEFAULT_POSTGRES_HOST
        );
        int port = integerValue(
            postgresConfig,
            DataServerConstants.POSTGRES_PORT_KEY,
            DataServerConstants.DEFAULT_POSTGRES_PORT
        );
        String database = stringValue(
            postgresConfig,
            DataServerConstants.POSTGRES_DATABASE_KEY,
            DataServerConstants.DEFAULT_POSTGRES_DATABASE
        );
        hikari.setJdbcUrl(
            DataServerConstants.POSTGRES_JDBC_URL_TEMPLATE.formatted(host, port, database)
        );
        hikari.setUsername(
            stringValue(
                postgresConfig,
                DataServerConstants.POSTGRES_USER_KEY,
                DataServerConstants.DEFAULT_POSTGRES_USER
            )
        );
        hikari.setPassword(
            requiredStringValue(
                postgresConfig,
                DataServerConstants.POSTGRES_PASSWORD_KEY,
                DataServerConstants.MISSING_POSTGRES_PASSWORD_MESSAGE
            )
        );
        hikari.setMaximumPoolSize(
            integerValue(
                postgresConfig,
                DataServerConstants.POSTGRES_POOL_MAX_KEY,
                DataServerConstants.DEFAULT_POSTGRES_POOL_MAX
            )
        );
        hikari.setMinimumIdle(Math.min(2, hikari.getMaximumPoolSize()));
        this.dataSource = new HikariDataSource(hikari);
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        PlatformTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        this.readCommittedTx = transactionTemplate(txManager, TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.serializableTx = transactionTemplate(txManager, TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    private static TransactionTemplate transactionTemplate(
            PlatformTransactionManager txManager, int isolationLevel) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setIsolationLevel(isolationLevel);
        return template;
    }

    private static String stringValue(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private static String requiredStringValue(
        Map<String, Object> values,
        String key,
        String missingValueMessage
    ) {
        String value = stringValue(values, key, "");
        if (value.isBlank()) {
            throw new DataClientException(
                DataServerConstants.INVALID_CONFIGURATION_KIND,
                missingValueMessage
            );
        }
        return value;
    }

    private static int integerValue(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }

    /**
     * Checks that the configured database accepts a lightweight query.
     *
     * @return true when the database connection is currently usable
     */
    @Override
    public boolean ping() {
        try {
            jdbc.getJdbcTemplate().execute(DataServerConstants.PING_SQL);
            return true;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    /**
     * Executes a named SQL mutation.
     *
     * @param statement logical statement name resolved by the command catalog
     * @param params named bind parameters for the SQL command
     * @return affected row count reported by Spring JDBC
     */
    @Override
    public int execute(String statement, Map<String, Object> params) {
        return executeSql(commandCatalog.sqlFor(statement), params, statement);
    }

    /**
     * Executes dynamically generated SQL (doc 07 §3.1 escape hatch, used only by
     * the generic schema-driven CRUD engine). Callers must have already validated
     * every identifier in {@code sql} against a known schema — values still arrive
     * only through {@code params}.
     *
     * @param sql fully-formed, identifier-validated SQL text with named placeholders
     * @param params named bind parameters for the SQL text
     * @return affected row count reported by Spring JDBC
     */
    @Override
    public int executeRaw(String sql, Map<String, Object> params) {
        return executeSql(sql, params, DataServerConstants.RAW_STATEMENT_LABEL);
    }

    private int executeSql(String sql, Map<String, Object> params, String statementLabel) {
        try {
            return jdbc.update(sql, params);
        } catch (DataIntegrityViolationException exception) {
            throw DataClientException.conflict(
                DataServerConstants.CONSTRAINT_VIOLATION_MESSAGE.formatted(statementLabel)
            );
        } catch (DataAccessException exception) {
            throw DataClientException.unavailable(
                DataServerConstants.EXECUTE_FAILURE_MESSAGE.formatted(statementLabel),
                exception
            );
        }
    }

    /**
     * Executes a named SQL query and maps each result row into a generic row wrapper.
     *
     * @param statement logical statement name resolved by the command catalog
     * @param params named bind parameters for the SQL command
     * @return ordered result rows returned by the SQL command
     */
    @Override
    public List<Row> query(String statement, Map<String, Object> params) {
        return querySql(commandCatalog.sqlFor(statement), params, statement);
    }

    /**
     * Executes dynamically generated SQL that returns rows. See
     * {@link #executeRaw(String, Map)} for the identifier-safety contract.
     *
     * @param sql fully-formed, identifier-validated SQL text with named placeholders
     * @param params named bind parameters for the SQL text
     * @return ordered result rows returned by the SQL text
     */
    @Override
    public List<Row> queryRaw(String sql, Map<String, Object> params) {
        return querySql(sql, params, DataServerConstants.RAW_STATEMENT_LABEL);
    }

    private List<Row> querySql(String sql, Map<String, Object> params, String statementLabel) {
        try {
            return jdbc.query(sql, params, PostgresDataServer::mapRow);
        } catch (DataAccessException exception) {
            throw DataClientException.unavailable(
                DataServerConstants.QUERY_FAILURE_MESSAGE.formatted(statementLabel),
                exception
            );
        }
    }

    private static Row mapRow(ResultSet rs, int rowNum) throws SQLException {
        var meta = rs.getMetaData();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            values.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return new Row(values);
    }

    /**
     * Executes a named SQL query that should return zero or one row.
     *
     * @param statement logical statement name resolved by the command catalog
     * @param params named bind parameters for the SQL command
     * @return optional row result when exactly one row is returned
     */
    @Override
    public Optional<Row> queryOne(String statement, Map<String, Object> params) {
        List<Row> rows = query(statement, params);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw DataClientException.conflict(
                DataServerConstants.QUERY_ONE_CONFLICT_MESSAGE.formatted(statement)
            );
        }
        return Optional.of(rows.get(0));
    }

    /**
     * Runs work within a Spring-managed transaction using the requested isolation level.
     *
     * @param isolation requested transaction isolation abstraction
     * @param work callback that receives this data client inside the transaction
     * @return callback result produced inside the transaction
     * @param <T> result type returned by the callback
     */
    @Override
    public <T> T transaction(Isolation isolation, Function<DataClient, T> work) {
        TransactionTemplate template = isolation == Isolation.SERIALIZABLE ? serializableTx : readCommittedTx;
        return template.execute(status -> work.apply(this));
    }

    /**
     * Returns a context-aware client view.
     *
     * @param context context values that may influence downstream data access
     * @return this instance until tenant-scoped propagation is implemented
     */
    @Override
    public DataClient withContext(Map<String, Object> context) {
        // Tenant GUC propagation is a follow-up item once multi-tenant tables exist.
        return this;
    }

    /**
     * Releases the Hikari connection pool. Safe to call more than once.
     */
    @Override
    public void close() {
        dataSource.close();
    }
}
