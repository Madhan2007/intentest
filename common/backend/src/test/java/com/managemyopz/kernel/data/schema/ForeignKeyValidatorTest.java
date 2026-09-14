/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.client.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForeignKeyValidatorTest {

    private static final ColumnDefinition ID = new ColumnDefinition(
        "id", ColumnType.UUID, false, false, true, null, null, null, "gen_random_uuid()");
    private static final ColumnDefinition COMPANY_ID = new ColumnDefinition(
        "company_id", ColumnType.UUID, true, false, false, null, null, null, null);

    private static final EntitySchema COMPANY = new EntitySchema(
        "company_information", "company_information", 1, List.of(ID), List.of(), List.of(), "OPZMAIN", true);
    private static final EntitySchema USER = new EntitySchema(
        "id_user", "id_user", 1, List.of(ID, COMPANY_ID), List.of(),
        List.of(new ForeignKeyDefinition(
            "company_id", "company_information", "id",
            ForeignKeyDefinition.OnDelete.RESTRICT, ForeignKeyDefinition.Enforce.POSTGRES)),
        "OPZUSER", false);

    @Mock private SchemaRegistry schemaRegistry;
    @Mock private DataClientRegistry dataClientRegistry;
    @Mock private DataClient dataClient;

    private ForeignKeyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ForeignKeyValidator(schemaRegistry, dataClientRegistry);
    }

    @Test
    @DisplayName("accepts a company_id that exists on the referenced database")
    void acceptsExistingReference() {
        UUID companyId = UUID.randomUUID();
        when(schemaRegistry.findByTable("company_information")).thenReturn(Optional.of(COMPANY));
        when(dataClientRegistry.resolve(COMPANY)).thenReturn(dataClient);
        when(dataClient.queryRaw(anyString(), anyMap())).thenReturn(List.of(new Row(Map.of("?column?", 1))));

        var errors = validator.validate(USER, Map.of("company_id", companyId.toString()), false);

        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("rejects a company_id that is missing from the referenced database")
    void rejectsMissingReference() {
        when(schemaRegistry.findByTable("company_information")).thenReturn(Optional.of(COMPANY));
        when(dataClientRegistry.resolve(COMPANY)).thenReturn(dataClient);
        when(dataClient.queryRaw(anyString(), anyMap())).thenReturn(List.of());

        var errors = validator.validate(USER, Map.of("company_id", UUID.randomUUID().toString()), false);

        assertThat(errors).extracting("field", "code").contains(tuple("company_id", "foreign_key"));
    }

    @Test
    @DisplayName("skips the check when the foreign-key field is omitted")
    void skipsOmittedField() {
        var errors = validator.validate(USER, Map.of(), true);
        assertThat(errors).isEmpty();
        verify(schemaRegistry, never()).findByTable(anyString());
    }
}
