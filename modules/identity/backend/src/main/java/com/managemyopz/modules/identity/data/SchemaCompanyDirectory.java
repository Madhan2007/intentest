/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Resolves a login prefix or email domain against company_references.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.data.schema.EntitySchema;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder;
import com.managemyopz.kernel.data.schema.GenericSqlBuilder.SqlCommand;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.data.schema.web.FilterSpec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Looks up {@code company_information.id} from a value stored in
 * {@code company_references}. No company names are hardcoded — the match is
 * whatever the YAML-backed table contains.
 */
public class SchemaCompanyDirectory {

    private static final int LOOKUP_LIMIT = 2;

    private final SchemaRegistry schemaRegistry;
    private final DataClientRegistry dataClientRegistry;

    /**
     * Creates the schema-driven company reference lookup.
     *
     * @param schemaRegistry loaded entity schemas
     * @param dataClientRegistry named-database router
     */
    public SchemaCompanyDirectory(SchemaRegistry schemaRegistry, DataClientRegistry dataClientRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.dataClientRegistry = dataClientRegistry;
    }

    /**
     * Finds the company whose references JSON array contains the typed value.
     *
     * @param reference login prefix or email domain
     * @return company id when exactly one row matches
     */
    public Optional<String> findIdByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        Optional<EntitySchema> schema = schemaRegistry.find(IdentityDataConstants.COMPANY_ENTITY);
        if (schema.isEmpty()) {
            return Optional.empty();
        }
        FilterSpec filter = new FilterSpec(
            null,
            null,
            null,
            null,
            null,
            0,
            LOOKUP_LIMIT,
            Map.of(IdentityDataConstants.COMPANY_REFERENCES_COLUMN, reference)
        );
        SqlCommand command = GenericSqlBuilder.selectFiltered(schema.get(), filter, LOOKUP_LIMIT, 0);
        List<Row> rows;
        try {
            rows = dataClientRegistry.resolve(schema.get()).queryRaw(command.sql(), command.params());
        } catch (DataClientException exception) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            return Optional.empty();
        }
        Object companyId = rows.getFirst().get(IdentityDataConstants.USER_ID_KEY);
        return companyId == null ? Optional.empty() : Optional.of(companyId.toString());
    }
}
