/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.broker;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

/** broker.type=memory — tests only (doc 07 §7). */
public class MemoryBrokerServer implements BrokerClient, AutoCloseable {

    private final ConcurrentHashMap<String, BlockingQueue<Map<String, Object>>> queues = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private BlockingQueue<Map<String, Object>> queueFor(String name) {
        return queues.computeIfAbsent(name, q -> new LinkedBlockingQueue<>());
    }

    @Override
    public void publish(String queue, Map<String, Object> command) {
        queueFor(queue).add(command);
    }

    @Override
    public void consume(String queue, String consumerGroup, BiConsumer<String, Map<String, Object>> handler) {
        executor.submit(() -> {
            BlockingQueue<Map<String, Object>> q = queueFor(queue);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Map<String, Object> msg = q.take();
                    handler.accept(queue, msg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    @Override
    public boolean ping() { return true; }

    /**
     * Stops consumer virtual threads and drops in-memory queues.
     */
    @Override
    public void close() {
        executor.shutdownNow();
        queues.clear();
    }
}
