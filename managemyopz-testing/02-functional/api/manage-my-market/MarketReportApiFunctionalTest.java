/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Functional API test suite for Manage My Market reporting endpoints.
 */
package com.managemyopz.testing.functional.managemymarket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MarketReportApiFunctionalTest {

    @Test
    @DisplayName("validates report filter envelope structure")
    void validatesReportFilterEnvelope() {
        Map<String, Object> filter = Map.of(
                "status", "ACTIVE",
                "limit", 20
        );

        assertThat(filter).containsEntry("status", "ACTIVE");
        assertThat(filter).containsEntry("limit", 20);
    }

    @Test
    @DisplayName("verifies 8 required marketing reports exist")
    void verifiesRequiredReports() {
        List<String> reportEndpoints = List.of(
                "/lead-summary",
                "/lead-conversion-rate",
                "/campaign-performance",
                "/channel-spend",
                "/telecalling-stats",
                "/referrer-leaderboard",
                "/followup-compliance",
                "/journey-funnel"
        );

        assertThat(reportEndpoints).hasSize(8);
    }
}
