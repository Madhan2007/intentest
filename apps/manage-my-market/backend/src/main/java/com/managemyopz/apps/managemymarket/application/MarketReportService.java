/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class MarketReportService {

    private static final Duration REPORT_CACHE_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};

    private final DataClient dataClient;
    private final CacheClient cacheClient;
    private final ObjectMapper objectMapper;

    public MarketReportService(DataClient dataClient, CacheClient cacheClient, ObjectMapper objectMapper) {
        this.dataClient = dataClient;
        this.cacheClient = cacheClient;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> getLeadSummary(UUID companyId, Instant fromDate, Instant toDate) {
        String cacheKey = "mkt:rpt:lead_summary:" + companyId + ":" + fromDate + ":" + toDate;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("from_date", fromDate != null ? fromDate : "");
        params.put("to_date", toDate != null ? toDate : "");
        return queryWithCache(cacheKey, "report.lead_summary", params);
    }

    public List<Map<String, Object>> getLeadConversionRate(UUID companyId, Instant fromDate, Instant toDate) {
        String cacheKey = "mkt:rpt:lead_conv:" + companyId + ":" + fromDate + ":" + toDate;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("from_date", fromDate != null ? fromDate : "");
        params.put("to_date", toDate != null ? toDate : "");
        return queryWithCache(cacheKey, "report.lead_conversion_rate", params);
    }

    public List<Map<String, Object>> getCampaignPerformance(UUID companyId, String status) {
        String cacheKey = "mkt:rpt:camp_perf:" + companyId + ":" + (status != null ? status : "ALL");
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("status", status != null ? status : "");
        return queryWithCache(cacheKey, "report.campaign_performance", params);
    }

    public List<Map<String, Object>> getChannelSpend(UUID companyId, Instant fromDate, Instant toDate) {
        String cacheKey = "mkt:rpt:chan_spend:" + companyId + ":" + fromDate + ":" + toDate;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("from_date", fromDate != null ? fromDate : "");
        params.put("to_date", toDate != null ? toDate : "");
        return queryWithCache(cacheKey, "report.channel_spend", params);
    }

    public List<Map<String, Object>> getTelecallingStats(UUID companyId, String agentUserId, Instant fromDate, Instant toDate) {
        String cacheKey = "mkt:rpt:telecall:" + companyId + ":" + agentUserId + ":" + fromDate + ":" + toDate;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("agent_user_id", agentUserId != null ? agentUserId : "");
        params.put("from_date", fromDate != null ? fromDate : "");
        params.put("to_date", toDate != null ? toDate : "");
        return queryWithCache(cacheKey, "report.telecalling_stats", params);
    }

    public List<Map<String, Object>> getReferrerLeaderboard(UUID companyId, String status, int limit) {
        String cacheKey = "mkt:rpt:ref_lead:" + companyId + ":" + status + ":" + limit;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("status", status != null ? status : "");
        params.put("limit", limit > 0 ? limit : 20);
        return queryWithCache(cacheKey, "report.referrer_leaderboard", params);
    }

    public List<Map<String, Object>> getFollowupCompliance(UUID companyId, Instant fromDate, Instant toDate) {
        String cacheKey = "mkt:rpt:followup_comp:" + companyId + ":" + fromDate + ":" + toDate;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("from_date", fromDate != null ? fromDate : "");
        params.put("to_date", toDate != null ? toDate : "");
        return queryWithCache(cacheKey, "report.followup_compliance", params);
    }

    public List<Map<String, Object>> getJourneyFunnel(UUID companyId, UUID journeyId) {
        String cacheKey = "mkt:rpt:journey_funnel:" + companyId + ":" + journeyId;
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("journey_id", journeyId != null ? journeyId : "");
        return queryWithCache(cacheKey, "report.journey_enrollment_funnel", params);
    }

    private List<Map<String, Object>> queryWithCache(String cacheKey, String commandName, Map<String, Object> params) {
        try {
            Optional<String> cached = cacheClient.get(cacheKey);
            if (cached.isPresent() && !cached.get().isBlank()) {
                return objectMapper.readValue(cached.get(), LIST_MAP_TYPE);
            }
        } catch (Exception ignored) {
            // cache fallback to DB
        }

        List<Row> rows = dataClient.query(commandName, params);
        List<Map<String, Object>> result = rows.stream().map(Row::values).toList();

        try {
            String json = objectMapper.writeValueAsString(result);
            cacheClient.set(cacheKey, json, REPORT_CACHE_TTL);
        } catch (Exception ignored) {
            // non-fatal
        }

        return result;
    }
}
