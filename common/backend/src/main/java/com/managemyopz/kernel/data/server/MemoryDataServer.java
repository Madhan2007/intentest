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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * db.type=memory — unit tests and kernel-only demos (doc 07 §4.2). Never
 * used when profile=prod (doctor rejects it).
 */
public class MemoryDataServer implements DataClient {

    private final MemoryCommandRegistry registry;
    private final Object transactionLock = new Object();

    /**
     * Creates an in-memory data client backed by registered command handlers.
     *
     * @param registry command registry that owns in-process handlers
     */
    public MemoryDataServer(MemoryCommandRegistry registry) {
        this.registry = registry;
    }

    /**
     * Reports that the in-memory implementation is always reachable in-process.
     *
     * @return always true for the in-memory server
     */
    @Override
    public boolean ping() {
        return true;
    }

    /**
     * Executes a registered in-memory mutation handler.
     *
     * @param statement logical command name
     * @param params command parameters
     * @return affected row count reported by the handler
     */
    @Override
    public int execute(String statement, Map<String, Object> params) {
        try {
            return registry.execute(statement, params);
        } catch (IllegalStateException exception) {
            throw DataClientException.unsupported(exception.getMessage());
        }
    }

    /**
     * Executes a registered in-memory query handler.
     *
     * @param statement logical command name
     * @param params command parameters
     * @return rows returned by the handler
     */
    @Override
    public List<Row> query(String statement, Map<String, Object> params) {
        try {
            return registry.query(statement, params);
        } catch (IllegalStateException exception) {
            throw DataClientException.unsupported(exception.getMessage());
        }
    }

    /**
     * Executes a registered in-memory query expecting at most one row.
     *
     * @param statement logical command name
     * @param params command parameters
     * @return optional row returned by the handler
     */
    @Override
    public Optional<Row> queryOne(String statement, Map<String, Object> params) {
        List<Row> rows = query(statement, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Runs in-memory work under a coarse-grained synchronization lock.
     *
     * @param isolation requested isolation level abstraction
     * @param work callback to execute while holding the transaction lock
     * @return callback result
     * @param <T> result type returned by the callback
     */
    @Override
    public <T> T transaction(Isolation isolation, Function<DataClient, T> work) {
        synchronized (transactionLock) {
            return work.apply(this);
        }
    }

    /**
     * Returns a context-aware client view.
     *
     * @param context context values that may influence downstream data access
     * @return this instance because the memory implementation ignores context
     */
    @Override
    public DataClient withContext(Map<String, Object> context) {
        return this;
    }
}
