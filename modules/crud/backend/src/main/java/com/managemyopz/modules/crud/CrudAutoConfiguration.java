/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-03
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.crud;

import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.data.schema.ForeignKeyValidator;
import com.managemyopz.kernel.data.schema.GenericValidationEngine;
import com.managemyopz.kernel.data.schema.SchemaRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.modules.crud.controller.GenericCrudController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers only when the `crud` folder is present and enabled (doc 01 §5 —
 * Java Module SPI). Removing modules/crud removes this whole @Configuration and
 * every bean it declares. The generic repository/service/controller work for any
 * entity declared under modules/*&#47;db/schema/*.yaml — the kernel's
 * {@link SchemaRegistry} and {@link GenericValidationEngine} are always-on kernel
 * beans (they gate per-schema module enablement themselves), never module-specific.
 */
@Configuration
@ConditionalOnModule("crud")
public class CrudAutoConfiguration {

    @Bean
    public GenericCrudRepository genericCrudRepository(DataClientRegistry dataClientRegistry) {
        return new GenericCrudRepository(dataClientRegistry);
    }

    @Bean
    public GenericCrudService genericCrudService(SchemaRegistry schemaRegistry, GenericCrudRepository repository,
                                                  GenericValidationEngine validationEngine,
                                                  ForeignKeyValidator foreignKeyValidator,
                                                  DataClientRegistry dataClientRegistry) {
        return new GenericCrudService(
            schemaRegistry, repository, validationEngine, foreignKeyValidator, dataClientRegistry);
    }

    @Bean
    public GenericCrudController genericCrudController(GenericCrudService crudService) {
        return new GenericCrudController(crudService);
    }
}
