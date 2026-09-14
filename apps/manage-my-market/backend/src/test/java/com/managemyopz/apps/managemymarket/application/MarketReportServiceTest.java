/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketReportServiceTest {

    private final DataClient dataClient = mock(DataClient.class);
    private final CacheClient cacheClient = mock(CacheClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MarketReportService reportService = new MarketReportService(dataClient, cacheClient, objectMapper);

    @Test
    @DisplayName("returns cached report data if present in CacheClient without querying database")
    void returnsCachedDataWhenPresent() {
        UUID companyId = UUID.randomUUID();
        String cacheKey = "mkt:rpt:lead_summary:" + companyId + ":null:null";
        String cachedJson = "[{\"status\":\"WON\",\"lead_source_type\":\"GOOGLE_ADS\",\"count\":15}]";

        when(cacheClient.get(cacheKey)).thenReturn(Optional.of(cachedJson));

        List<Map<String, Object>> result = reportService.getLeadSummary(companyId, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().get("status")).isEqualTo("WON");
        verifyNoInteractions(dataClient);
    }

    @Test
    @DisplayName("queries database and caches result when cache misses")
    void queriesDatabaseAndCachesOnMiss() {
        UUID companyId = UUID.randomUUID();
        String cacheKey = "mkt:rpt:lead_summary:" + companyId + ":null:null";

        when(cacheClient.get(cacheKey)).thenReturn(Optional.empty());

        Row mockRow = new Row(Map.of(
                "status", "NEW",
                "lead_source_type", "INBOUND_WEB",
                "count", 25L,
                "total_estimated_value", BigDecimal.valueOf(100000)
        ));
        when(dataClient.query(eq("report.lead_summary"), any())).thenReturn(List.of(mockRow));

        List<Map<String, Object>> result = reportService.getLeadSummary(companyId, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().get("status")).isEqualTo("NEW");
        verify(dataClient).query(eq("report.lead_summary"), any());
        verify(cacheClient).set(eq(cacheKey), anyString(), eq(Duration.ofMinutes(5)));
    }
}
