/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Package constants for kernel data server implementations.
 */
package com.managemyopz.kernel.data.server;

final class DataServerConstants {

    static final String POSTGRES_HOST_KEY = "host";
    static final String DEFAULT_POSTGRES_HOST = "postgres";
    static final String POSTGRES_PORT_KEY = "port";
    static final int DEFAULT_POSTGRES_PORT = 5432;
    static final String POSTGRES_DATABASE_KEY = "database";
    static final String DEFAULT_POSTGRES_DATABASE = "erp";
    static final String POSTGRES_USER_KEY = "user";
    static final String DEFAULT_POSTGRES_USER = "erp_app";
    static final String POSTGRES_PASSWORD_KEY = "password";
    static final String INVALID_CONFIGURATION_KIND = "invalid_configuration";
    static final String MISSING_POSTGRES_PASSWORD_MESSAGE =
        "db.postgres.password must be configured";
    static final String POSTGRES_POOL_MAX_KEY = "pool_max";
    static final int DEFAULT_POSTGRES_POOL_MAX = 8;
    static final String POSTGRES_JDBC_URL_TEMPLATE = "jdbc:postgresql://%s:%d/%s";
    static final String PING_SQL = "SELECT 1";
    static final String EXECUTE_FAILURE_MESSAGE = "execute failed: %s";
    static final String QUERY_FAILURE_MESSAGE = "query failed: %s";
    static final String QUERY_ONE_CONFLICT_MESSAGE = "Expected at most one row for: %s";
    static final String RAW_STATEMENT_LABEL = "dynamic SQL";
    static final String CONSTRAINT_VIOLATION_MESSAGE =
        "constraint violation (%s): referenced row may not exist or a unique constraint failed";
    static final String SQL_RESOURCE_PATTERN = "classpath*:modules/*/db/commands/*.sql";
    static final String SQL_FILE_SUFFIX = ".sql";
    static final String MISSING_SQL_MESSAGE = "No SQL command registered for: %s";
    static final String MISSING_MEMORY_QUERY_MESSAGE =
        "No memory query handler registered for: %s";
    static final String MISSING_MEMORY_EXECUTE_MESSAGE =
        "No memory execute handler registered for: %s";

    private DataServerConstants() {
    }
}