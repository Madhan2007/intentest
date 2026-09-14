/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Real-time data endpoints for Manage My Market dashboard widgets.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.application.MarketReportService;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/widgets")
public class MarketWidgetDataController {

    private final DataClient dataClient;
    private final MarketReportService reportService;

    public MarketWidgetDataController(DataClient dataClient, MarketReportService reportService) {
        this.dataClient = dataClient;
        this.reportService = reportService;
    }

    @GetMapping("/{widgetKey}")
    public ResponseEntity<ApiEnvelope<Object>> getWidgetData(
        @PathVariable("widgetKey") String widgetKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Object data = resolveWidgetData(widgetKey, session.getCompanyId());
        return ResponseEntity.ok(ApiEnvelope.ok(data, correlationId(request)));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> getWidgetsBatch(
        @RequestBody List<String> widgetKeys,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> result = new LinkedHashMap<>();
        if (widgetKeys != null) {
            for (String key : widgetKeys) {
                result.put(key, resolveWidgetData(key, session.getCompanyId()));
            }
        }
        return ResponseEntity.ok(ApiEnvelope.ok(result, correlationId(request)));
    }

    private Object resolveWidgetData(String widgetKey, UUID companyId) {
        return switch (widgetKey) {
            case "mkt-kpi-leads" -> {
                Optional<Row> row = dataClient.queryOne("widget.kpi_summary", Map.of("company_id", companyId));
                yield row.map(r -> Map.of(
                        "total_leads", r.getLong("total_leads"),
                        "new_leads_today", r.getLong("new_leads_today"),
                        "total_estimated_value", r.getDecimal("total_estimated_value")
                )).orElse(Map.of("total_leads", 0, "new_leads_today", 0, "total_estimated_value", 0));
            }
            case "mkt-kpi-conversion" -> {
                List<Map<String, Object>> convRows = reportService.getLeadConversionRate(companyId, null, null);
                long total = 0;
                long won = 0;
                for (Map<String, Object> row : convRows) {
                    if (row.get("total_leads") instanceof Number n) total += n.longValue();
                    if (row.get("converted_leads") instanceof Number n) won += n.longValue();
                }
                double rate = total > 0 ? ((double) won / total) * 100.0 : 0.0;
                yield Map.of("total_leads", total, "won_leads", won, "conversion_rate_pct", Math.round(rate * 100.0) / 100.0);
            }
            case "mkt-kpi-campaigns" -> {
                Optional<Row> row = dataClient.queryOne("widget.kpi_summary", Map.of("company_id", companyId));
                yield row.map(r -> Map.of(
                        "active_campaigns", r.getLong("active_campaigns"),
                        "active_campaigns_budget", r.getDecimal("active_campaigns_budget")
                )).orElse(Map.of("active_campaigns", 0, "active_campaigns_budget", 0));
            }
            case "mkt-kpi-telecalling" -> {
                Optional<Row> row = dataClient.queryOne("widget.kpi_summary", Map.of("company_id", companyId));
                yield row.map(r -> Map.of(
                        "calls_today", r.getLong("calls_today"),
                        "pending_queue_items", r.getLong("pending_queue_items")
                )).orElse(Map.of("calls_today", 0, "pending_queue_items", 0));
            }
            case "mkt-lead-funnel" -> reportService.getLeadSummary(companyId, null, null);
            case "mkt-campaign-roi" -> reportService.getCampaignPerformance(companyId, null);
            case "mkt-recent-leads" -> {
                List<Row> rows = dataClient.query("lead.list_paged", Map.of(
                        "company_id", companyId,
                        "status", "",
                        "lead_source_type", "",
                        "owner_user_id", "",
                        "search", "",
                        "limit", 10,
                        "offset", 0
                ));
                yield rows.stream().map(Row::values).toList();
            }
            case "mkt-call-queue-status" -> {
                List<Row> rows = dataClient.query("call_queue.list_by_company", Map.of(
                        "company_id", companyId,
                        "status", "ACTIVE"
                ));
                yield rows.stream().map(Row::values).toList();
            }
            default -> Map.of();
        };
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
