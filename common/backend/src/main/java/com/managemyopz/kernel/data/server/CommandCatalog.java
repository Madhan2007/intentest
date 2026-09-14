/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.server;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.annotation.PostConstruct;

/**
 * Loads named SQL commands from classpath modules/&#42;/db/commands/&#42;.sql
 * (doc 07 §3.1). Filename without extension is the command name, e.g.
 * modules/identity/db/commands/identity.find_user_by_username.sql. The
 * kernel never hardcodes a module id here — it only scans the folder shape.
 */
@Component
public class CommandCatalog {

    private final Map<String, String> commands = new LinkedHashMap<>();

    @PostConstruct
    void load() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(DataServerConstants.SQL_RESOURCE_PATTERN);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            String name = filename.endsWith(DataServerConstants.SQL_FILE_SUFFIX)
                ? filename.substring(0, filename.length() - DataServerConstants.SQL_FILE_SUFFIX.length())
                : filename;
            try (InputStream in = resource.getInputStream()) {
                commands.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Resolves a logical command name to SQL text.
     *
     * @param commandName logical command name derived from an SQL filename
     * @return SQL text associated with the command name
     */
    public String sqlFor(String commandName) {
        String sql = commands.get(commandName);
        if (sql == null) {
            throw new IllegalStateException(
                DataServerConstants.MISSING_SQL_MESSAGE.formatted(commandName)
            );
        }
        return sql;
    }

    /**
     * Reports whether a logical SQL command exists in the catalog.
     *
     * @param commandName logical command name derived from an SQL filename
     * @return true when the command has been loaded from classpath resources
     */
    public boolean has(String commandName) {
        return commands.containsKey(commandName);
    }
}
