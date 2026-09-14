/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

/** Resource glob, identifier/default-expression allowlists, and pagination defaults
 *  for the generic schema-driven CRUD engine (doc 07 §3.1 escape hatch). */
public final class SchemaConstants {

    public static final String SCHEMA_RESOURCE_PATTERN = "classpath*:modules/*/db/schema/*.yaml";

    /** Lower-snake, Postgres-identifier-safe, <= 63 bytes (Postgres's own NAMEDATALEN limit). */
    public static final String IDENTIFIER_PATTERN = "^[a-z][a-z0-9_]{0,62}$";

    /** Only these literal/function tokens are accepted as a column DEFAULT — never
     *  arbitrary text, so a schema YAML can never become a SQL injection vector.
     *  '{}' is allowlisted separately as the one supported array-column default. */
    public static final String DEFAULT_EXPR_PATTERN =
        "^(now\\(\\)|gen_random_uuid\\(\\)|true|false|-?\\d+|'[A-Za-z0-9 _.-]{0,64}'|'\\{\\}')$";

    /** A `database:` value is a pure Java lookup key into DataClientRegistry's
     *  config map — never spliced into SQL text, so it does not need SQL-identifier
     *  safety, only a sanity bound matching the uppercase convention (OPZUSER,
     *  OPZMAIN) already used for these names. */
    public static final String DATABASE_KEY_PATTERN = "^[A-Z][A-Z0-9_]{0,62}$";

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;

    private SchemaConstants() {
    }
}
