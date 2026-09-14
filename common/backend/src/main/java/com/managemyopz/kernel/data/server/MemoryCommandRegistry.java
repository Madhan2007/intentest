/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.server;

import com.managemyopz.kernel.data.client.Row;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry of in-process command handlers used only by MemoryDataServer
 * (doc 07 §4.2 — "tables as concurrent maps... enough for field-level
 * service tests; not a Postgres emulator"). Modules register their own
 * handlers at startup; the kernel never references a module id here.
 */
@Component
public class MemoryCommandRegistry {

    private final Map<String, Function<Map<String, Object>, List<Row>>> queries = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String, Object>, Integer>> executes = new ConcurrentHashMap<>();

    /**
     * Registers a named in-memory query handler.
     *
     * @param name logical command name
     * @param handler query handler implementation
     */
    public void registerQuery(String name, Function<Map<String, Object>, List<Row>> handler) {
        queries.put(name, handler);
    }

    /**
     * Registers a named in-memory mutation handler.
     *
     * @param name logical command name
     * @param handler mutation handler implementation
     */
    public void registerExecute(String name, Function<Map<String, Object>, Integer> handler) {
        executes.put(name, handler);
    }

    /**
     * Executes a registered query handler.
     *
     * @param name logical command name
     * @param params named command parameters
     * @return rows produced by the registered handler
     */
    public List<Row> query(String name, Map<String, Object> params) {
        Function<Map<String, Object>, List<Row>> handler = queries.get(name);
        if (handler == null) {
            throw new IllegalStateException(
                DataServerConstants.MISSING_MEMORY_QUERY_MESSAGE.formatted(name)
            );
        }
        return handler.apply(params);
    }

    /**
     * Executes a registered mutation handler.
     *
     * @param name logical command name
     * @param params named command parameters
     * @return affected row count returned by the registered handler
     */
    public int execute(String name, Map<String, Object> params) {
        Function<Map<String, Object>, Integer> handler = executes.get(name);
        if (handler == null) {
            throw new IllegalStateException(
                DataServerConstants.MISSING_MEMORY_EXECUTE_MESSAGE.formatted(name)
            );
        }
        return handler.apply(params);
    }
}
