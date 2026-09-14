/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.cache.server;

import com.managemyopz.kernel.cache.client.CacheClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * cache.type=memory — single process only, forbidden when replica count > 1
 * or profile=prod (doc 07 §6, doctor rule).
 */
public class MemoryCacheServer implements CacheClient {

    private record Entry(String value, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicInteger writesSinceSweep = new AtomicInteger();
    private static final int SWEEP_EVERY_WRITES = 64;

    private record Subscription(Pattern pattern, Consumer<String> handler) {}

    @Override
    public Optional<String> get(String key) {
        Entry e = store.get(key);
        if (e == null) return Optional.empty();
        if (e.expiresAt() != null && Instant.now().isAfter(e.expiresAt())) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(e.value());
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        Instant expiry = ttl == null ? null : Instant.now().plus(ttl);
        store.put(key, new Entry(value, expiry));
        maybeSweepExpired();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public synchronized boolean setIfAbsent(String key, String value, Duration ttl) {
        if (get(key).isPresent()) return false;
        set(key, value, ttl);
        return true;
    }

    @Override
    public long incr(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void publish(String topic, String payload) {
        for (Subscription s : subscriptions) {
            if (s.pattern().matcher(topic).matches()) {
                s.handler().accept(payload);
            }
        }
    }

    @Override
    public void subscribe(String topicPattern, Consumer<String> handler) {
        String regex = topicPattern.replace(".", "\\.").replace("*", ".*");
        subscriptions.add(new Subscription(Pattern.compile(regex), handler));
    }

    @Override
    public boolean ping() { return true; }

    private void maybeSweepExpired() {
        if (writesSinceSweep.incrementAndGet() % SWEEP_EVERY_WRITES != 0) {
            return;
        }
        Instant now = Instant.now();
        store.entrySet().removeIf(entry ->
            entry.getValue().expiresAt() != null && now.isAfter(entry.getValue().expiresAt()));
    }
}
