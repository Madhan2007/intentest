/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Kernel data port (doc 07 §3). Application services depend only on this
 * interface; `db.type` selects the implementing DataServer.
 */
public interface DataClient {

    boolean ping();

    int execute(String statement, Map<String, Object> params);

    List<Row> query(String statement, Map<String, Object> params);

    Optional<Row> queryOne(String statement, Map<String, Object> params);

    <T> T transaction(Isolation isolation, Function<DataClient, T> work);

    DataClient withContext(Map<String, Object> context);

    /**
     * Kernel-only escape hatch for dynamically generated, identifier-validated SQL
     * (doc 07 §3.1) — used solely by the generic schema-driven CRUD engine
     * (`com.managemyopz.kernel.data.schema`), never called from `modules/*` directly.
     * Callers must never splice unvalidated identifiers or client-controlled text
     * into {@code sql}; values always arrive via {@code params}. Default throws so
     * existing DataClient implementors/mocks do not need to implement it.
     */
    default int executeRaw(String sql, Map<String, Object> params) {
        throw DataClientException.unsupported("executeRaw is not supported by this DataClient");
    }

    /** See {@link #executeRaw(String, Map)}. */
    default List<Row> queryRaw(String sql, Map<String, Object> params) {
        throw DataClientException.unsupported("queryRaw is not supported by this DataClient");
    }
}
