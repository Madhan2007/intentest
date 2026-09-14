/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import java.util.Objects;

public record ForeignKeyDefinition(
    String column,
    String referencesTable,
    String referencesColumn,
    OnDelete onDelete,
    Enforce enforce
) {
    /** How Postgres should react when the referenced row is deleted. */
    public enum OnDelete { RESTRICT, CASCADE, SET_NULL }

    /** Where the relationship is checked: a real Postgres constraint, an app-level
     *  pre-check (for an optional module's table that might not be installed, or
     *  a referenced table that lives on another named database), or not checked
     *  at all (a deliberately soft/logical reference). */
    public enum Enforce { POSTGRES, APP, NONE }

    /**
     * Resolves how this foreign key is actually enforced.
     *
     * <p>Postgres cannot create a constraint across physical databases, so a
     * YAML {@code enforce: postgres} relationship is treated as {@link Enforce#APP}
     * whenever the source and referenced schemas declare different
     * {@code database:} keys (including default vs named). Same-database keys
     * keep the YAML value.</p>
     *
     * @param sourceDatabase {@code database:} of the declaring schema, or null for the default connection
     * @param referencedDatabase {@code database:} of the referenced schema, or null for the default connection
     * @return the enforcement mode the reconciler and write validator must use
     */
    public Enforce effectiveEnforce(String sourceDatabase, String referencedDatabase) {
        if (enforce == Enforce.NONE) {
            return Enforce.NONE;
        }
        if (!Objects.equals(sourceDatabase, referencedDatabase)) {
            return Enforce.APP;
        }
        return enforce;
    }
}
