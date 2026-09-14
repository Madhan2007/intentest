/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.config;

import com.managemyopz.kernel.broker.BrokerClient;
import com.managemyopz.kernel.broker.MemoryBrokerServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * broker.type factory (doc 07 §8). `valkey` (Streams) is documented in
 * doc 11 for a later drop; this pass ships `memory` for dev/tests and fails
 * fast on anything else — including `kafka`, which is reserved and must not
 * be implemented.
 */
@Configuration
public class BrokerClientConfig {

    @Bean
    public BrokerClient brokerClient(PlatformProperties props) {
        String type = props.getBroker().getType();
        return switch (type) {
            case "memory" -> new MemoryBrokerServer();
            case "kafka" -> throw new IllegalStateException("broker.type=kafka is reserved and not implemented (doc 11)");
            default -> throw new IllegalStateException("Unknown or not-yet-implemented broker.type: " + type);
        };
    }
}
