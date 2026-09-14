/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for marketing reports.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.ReportFilterRequest;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.application.MarketReportService;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/reports")
public class MarketReportController {

    private final MarketReportService reportService;

    public MarketReportController(MarketReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/lead-summary")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getLeadSummary(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getLeadSummary(
            session.getCompanyId(),
            filter != null ? filter.fromDate() : null,
            filter != null ? filter.toDate() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/lead-conversion-rate")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getLeadConversionRate(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getLeadConversionRate(
            session.getCompanyId(),
            filter != null ? filter.fromDate() : null,
            filter != null ? filter.toDate() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/campaign-performance")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getCampaignPerformance(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getCampaignPerformance(
            session.getCompanyId(),
            filter != null ? filter.status() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/channel-spend")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getChannelSpend(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getChannelSpend(
            session.getCompanyId(),
            filter != null ? filter.fromDate() : null,
            filter != null ? filter.toDate() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/telecalling-stats")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALLS, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getTelecallingStats(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getTelecallingStats(
            session.getCompanyId(),
            filter != null ? filter.agentUserId() : null,
            filter != null ? filter.fromDate() : null,
            filter != null ? filter.toDate() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/referrer-leaderboard")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRER, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getReferrerLeaderboard(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        int limit = filter != null && filter.limit() != null ? filter.limit() : 20;
        var list = reportService.getReferrerLeaderboard(
            session.getCompanyId(),
            filter != null ? filter.status() : null,
            limit
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/followup-compliance")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getFollowupCompliance(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var list = reportService.getFollowupCompliance(
            session.getCompanyId(),
            filter != null ? filter.fromDate() : null,
            filter != null ? filter.toDate() : null
        );
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/journey-funnel")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "v")
    public ResponseEntity<ApiEnvelope<List<Map<String, Object>>>> getJourneyFunnel(
        @RequestBody(required = false) ReportFilterRequest filter,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UUID journeyId = null;
        if (filter != null && filter.journeyId() != null && !filter.journeyId().isBlank()) {
            try {
                journeyId = UUID.fromString(filter.journeyId());
            } catch (IllegalArgumentException ignored) {}
        }

        var list = reportService.getJourneyFunnel(session.getCompanyId(), journeyId);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
