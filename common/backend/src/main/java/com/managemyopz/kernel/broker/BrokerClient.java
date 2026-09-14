/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.broker;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Kernel async job port (doc 07 §7). broker.type=valkey ships Streams in
 * this drop; Kafka is reserved and not implemented (doc 11).
 */
public interface BrokerClient {

    void publish(String queue, Map<String, Object> command);

    void consume(String queue, String consumerGroup, BiConsumer<String, Map<String, Object>> handler);

    boolean ping();
}
