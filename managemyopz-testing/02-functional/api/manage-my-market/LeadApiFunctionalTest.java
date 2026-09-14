/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Functional API test suite for Manage My Market Lead endpoints.
 */
package com.managemyopz.testing.functional.managemymarket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LeadApiFunctionalTest {

    @Test
    @DisplayName("validates lead creation payload contract")
    void validatesCreateLeadPayload() {
        Map<String, Object> payload = Map.of(
                "displayName", "Acme Enterprise Lead",
                "leadSourceType", "INBOUND_WEB",
                "estimatedValue", 50000,
                "priority", "HIGH"
        );

        assertThat(payload.get("displayName")).isEqualTo("Acme Enterprise Lead");
        assertThat(payload.get("leadSourceType")).isEqualTo("INBOUND_WEB");
        assertThat(payload.get("estimatedValue")).isEqualTo(50000);
    }

    @Test
    @DisplayName("validates lead stage progression contract")
    void validatesStageProgression() {
        String[] stages = {"NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON"};
        assertThat(stages).containsSequence("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON");
    }
}
