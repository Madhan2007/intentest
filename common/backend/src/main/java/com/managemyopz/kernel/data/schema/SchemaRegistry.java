/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-04
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.schema;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.module.ModuleCatalog;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans classpath modules/&#42;/db/schema/&#42;.yaml (present on disk and belonging to
 * an enabled module — same discovery-and-enablement contract as {@link ModuleCatalog}
 * itself) and builds the entity registry the generic CRUD engine reads at request
 * and reconciliation time. A malformed schema file is a fatal boot-time error,
 * never a runtime surprise.
 */
@Component
public class SchemaRegistry {

    private static final Pattern MODULE_ID_PATTERN =
        Pattern.compile(".*[/\\\\]modules[/\\\\]([^/\\\\]+)[/\\\\]db[/\\\\]schema[/\\\\].*");

    private final ModuleCatalog moduleCatalog;
    private final Map<String, EntitySchema> entities = new LinkedHashMap<>();
    private final Map<String, EntitySchema> tables = new LinkedHashMap<>();

    public SchemaRegistry(ModuleCatalog moduleCatalog) {
        this.moduleCatalog = moduleCatalog;
    }

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(SchemaConstants.SCHEMA_RESOURCE_PATTERN);
        Yaml yaml = new Yaml();
        Set<String> seenTables = new HashSet<>();

        for (Resource resource : resources) {
            String moduleId = extractModuleId(resource);
            if (moduleId == null || !moduleCatalog.isEnabled(moduleId)) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> doc = yaml.load(in);
                if (doc == null) {
                    continue;
                }
                EntitySchema schema = parse(doc, resource.getFilename());
                if (entities.containsKey(schema.entity())) {
                    throw new IllegalStateException("Duplicate entity schema: " + schema.entity());
                }
                if (!seenTables.add(schema.table())) {
                    throw new IllegalStateException("Duplicate table name across schemas: " + schema.table());
                }
                entities.put(schema.entity(), schema);
                tables.put(schema.table(), schema);
            }
        }
    }

    private String extractModuleId(Resource resource) throws IOException {
        Matcher matcher = MODULE_ID_PATTERN.matcher(resource.getURL().toString());
        return matcher.matches() ? matcher.group(1) : null;
    }

    @SuppressWarnings("unchecked")
    EntitySchema parse(Map<String, Object> doc, String filename) {
        String entity = (String) doc.get("entity");
        String table = doc.get("table") != null ? (String) doc.get("table") : entity;
        SchemaIdentifierValidator.requireValidIdentifier(entity, "entity");
        SchemaIdentifierValidator.requireValidIdentifier(table, "table");
        int version = doc.get("version") instanceof Number n ? n.intValue() : 1;

        List<ColumnDefinition> columns = new ArrayList<>();
        for (Map<String, Object> col : (List<Map<String, Object>>) doc.getOrDefault("columns", List.of())) {
            columns.add(parseColumn(col));
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("Schema " + filename + " declares no columns");
        }
        if (columns.stream().noneMatch(ColumnDefinition::primaryKey)) {
            throw new IllegalStateException("Schema " + filename + " declares no primary_key column");
        }

        List<IndexDefinition> indexes = new ArrayList<>();
        for (Map<String, Object> idx : (List<Map<String, Object>>) doc.getOrDefault("indexes", List.of())) {
            indexes.add(parseIndex(idx));
        }

        List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
        for (Map<String, Object> fk : (List<Map<String, Object>>) doc.getOrDefault("foreign_keys", List.of())) {
            foreignKeys.add(parseForeignKey(fk));
        }

        String database = (String) doc.get("database");
        if (database != null) {
            SchemaIdentifierValidator.requireValidDatabaseKey(database);
        }
        boolean exposeGenericApi = !Boolean.FALSE.equals(doc.get("expose_generic_api"));

        return new EntitySchema(entity, table, version, columns, indexes, foreignKeys, database, exposeGenericApi);
    }

    ColumnDefinition parseColumn(Map<String, Object> col) {
        String name = (String) col.get("name");
        SchemaIdentifierValidator.requireValidIdentifier(name, "column");
        ColumnType type = ColumnType.fromYamlName((String) col.get("type"));
        boolean nullable = !Boolean.FALSE.equals(col.get("nullable"));
        boolean unique = Boolean.TRUE.equals(col.get("unique"));
        boolean primaryKey = Boolean.TRUE.equals(col.get("primary_key"));
        Integer maxLength = col.get("max_length") instanceof Number n ? n.intValue() : null;
        Integer precision = col.get("precision") instanceof Number n ? n.intValue() : null;
        Integer scale = col.get("scale") instanceof Number n ? n.intValue() : null;
        String defaultExpr = (String) col.get("default");
        SchemaIdentifierValidator.requireValidDefaultExpression(defaultExpr);
        return new ColumnDefinition(name, type, nullable, unique, primaryKey, maxLength, precision, scale, defaultExpr);
    }

    @SuppressWarnings("unchecked")
    IndexDefinition parseIndex(Map<String, Object> idx) {
        String name = (String) idx.get("name");
        SchemaIdentifierValidator.requireValidIdentifier(name, "index");
        List<String> columns = (List<String>) idx.get("columns");
        for (String column : columns) {
            SchemaIdentifierValidator.requireValidIdentifier(column, "index column");
        }
        boolean unique = Boolean.TRUE.equals(idx.get("unique"));
        String order = idx.get("order") != null ? idx.get("order").toString() : "asc";
        return new IndexDefinition(name, columns, unique, order);
    }

    ForeignKeyDefinition parseForeignKey(Map<String, Object> fk) {
        String column = (String) fk.get("column");
        String referencesTable = (String) fk.get("references_table");
        String referencesColumn = (String) fk.get("references_column");
        SchemaIdentifierValidator.requireValidIdentifier(column, "foreign key column");
        SchemaIdentifierValidator.requireValidIdentifier(referencesTable, "foreign key referenced table");
        SchemaIdentifierValidator.requireValidIdentifier(referencesColumn, "foreign key referenced column");
        ForeignKeyDefinition.OnDelete onDelete = fk.get("on_delete") != null
            ? ForeignKeyDefinition.OnDelete.valueOf(fk.get("on_delete").toString().toUpperCase())
            : ForeignKeyDefinition.OnDelete.RESTRICT;
        ForeignKeyDefinition.Enforce enforce = fk.get("enforce") != null
            ? ForeignKeyDefinition.Enforce.valueOf(fk.get("enforce").toString().toUpperCase())
            : ForeignKeyDefinition.Enforce.POSTGRES;
        return new ForeignKeyDefinition(column, referencesTable, referencesColumn, onDelete, enforce);
    }

    public Optional<EntitySchema> find(String entity) {
        return Optional.ofNullable(entities.get(entity));
    }

    /**
     * Finds a schema by physical table name. Table names are unique across the
     * registry, including tables that live on different named databases.
     *
     * @param table physical Postgres table name from YAML {@code table:} or {@code entity:}
     * @return matching schema when one loaded entity uses that table
     */
    public Optional<EntitySchema> findByTable(String table) {
        if (table == null || table.isBlank()) {
            return Optional.empty();
        }
        EntitySchema indexed = tables.get(table);
        if (indexed != null) {
            return Optional.of(indexed);
        }
        return entities.values().stream()
            .filter(schema -> table.equals(schema.table()))
            .findFirst();
    }

    /**
     * Resolves an entity for the generic CRUD engine only. An entity with
     * {@code expose_generic_api: false} (e.g. identity's id_user) is treated
     * identically to an unknown entity — callers cannot distinguish "hidden"
     * from "does not exist". {@link #all()} is unaffected, since reconciliation
     * must still manage a hidden entity's table.
     */
    public EntitySchema require(String entity) {
        return find(entity)
            .filter(EntitySchema::exposeGenericApi)
            .orElseThrow(() -> DataClientException.notFound("Unknown entity: " + entity));
    }

    public Collection<EntitySchema> all() {
        return Collections.unmodifiableCollection(entities.values());
    }
}
