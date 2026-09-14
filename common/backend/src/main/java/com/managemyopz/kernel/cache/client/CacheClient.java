/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.cache.client;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/** Kernel cache/session/pub-sub port (doc 07 §5). `cache.type` selects the server. */
public interface CacheClient {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);

    /** Returns true if the key was absent and is now set (session locks / idempotency). */
    boolean setIfAbsent(String key, String value, Duration ttl);

    long incr(String key);

    void publish(String topic, String payload);

    void subscribe(String topicPattern, Consumer<String> handler);

    boolean ping();
}
