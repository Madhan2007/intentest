/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.cache.server;

import com.managemyopz.kernel.cache.client.CacheClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * cache.type=valkey — production CacheServer using the Redis-compatible
 * Valkey protocol over Lettuce (doc 07 §6.1). Pub/Sub uses a dedicated
 * connection so it never blocks request/response commands.
 */
public class ValkeyCacheServer implements CacheClient, AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final String keyPrefix;

    public ValkeyCacheServer(Map<String, Object> valkeyConfig) {
        String host = str(valkeyConfig, "host", "valkey");
        int port = Integer.parseInt(str(valkeyConfig, "port", "6380"));
        boolean tls = Boolean.parseBoolean(str(valkeyConfig, "tls", "true"));
        String password = str(valkeyConfig, "password", null);
        this.keyPrefix = str(valkeyConfig, "key_prefix", "erp") + ":";

        RedisURI.Builder builder = RedisURI.builder().withHost(host).withPort(port).withSsl(tls);
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }
        this.client = RedisClient.create(builder.build());
        this.connection = client.connect();
        this.pubSubConnection = client.connectPubSub();
    }

    private static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : v.toString();
    }

    private String k(String key) { return keyPrefix + key; }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(connection.sync().get(k(key)));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (ttl == null) {
            connection.sync().set(k(key), value);
        } else {
            connection.sync().set(k(key), value, SetArgs.Builder.ex(ttl.toSeconds()));
        }
    }

    @Override
    public void delete(String key) {
        connection.sync().del(k(key));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        SetArgs args = SetArgs.Builder.nx();
        if (ttl != null) args = args.ex(ttl.toSeconds());
        String result = connection.sync().set(k(key), value, args);
        return "OK".equals(result);
    }

    @Override
    public long incr(String key) {
        return connection.sync().incr(k(key));
    }

    @Override
    public void publish(String topic, String payload) {
        connection.sync().publish(k(topic), payload);
    }

    @Override
    public void subscribe(String topicPattern, Consumer<String> handler) {
        pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String pattern, String channel, String message) {
                handler.accept(message);
            }
        });
        pubSubConnection.sync().psubscribe(k(topicPattern));
    }

    @Override
    public boolean ping() {
        return "PONG".equalsIgnoreCase(connection.sync().ping());
    }

    @Override
    public void close() {
        connection.close();
        pubSubConnection.close();
        client.shutdown();
    }
}
