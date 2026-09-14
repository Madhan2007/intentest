/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Lead follow-up operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.CreateFollowupRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateFollowupRequest;
import com.managemyopz.apps.managemymarket.application.LeadFollowupService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.LeadFollowup;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Controller exposing Lead follow-up endpoints.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/leads")
public class LeadFollowupController {

    private final LeadFollowupService followupService;

    public LeadFollowupController(LeadFollowupService followupService) {
        this.followupService = followupService;
    }

    @PostMapping("/{id}/followups")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "c")
    public ResponseEntity<ApiEnvelope<LeadFollowup>> createFollowup(
        @PathVariable("id") String leadId,
        @RequestBody CreateFollowupRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        LeadFollowup followup = followupService.createFollowup(
            leadId,
            body.reason(),
            body.scheduledAt(),
            body.assignedAgentUserId() != null ? body.assignedAgentUserId() : session.getUserId(),
            body.priority(),
            body.notes()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(followup, correlationId(request)));
    }

    @PostMapping("/{id}/followups/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<LeadFollowup>>> readFollowups(
        @PathVariable("id") String leadId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<LeadFollowup> list = followupService.listByLead(leadId);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping("/{id}/followups")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> updateFollowup(
        @PathVariable("id") String leadId,
        @RequestBody UpdateFollowupRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        followupService.updateFollowup(
            body.id(),
            leadId,
            body.reason(),
            body.scheduledAt(),
            body.assignedAgentUserId(),
            body.priority(),
            body.status(),
            body.notes()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @GetMapping("/followups/due")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<LeadFollowup>>> listDue(
        @RequestParam(name = "assignedAgentUserId", required = false) String assignedAgentUserId,
        @RequestParam(name = "dueBefore", required = false) Instant dueBefore,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<LeadFollowup> list = followupService.listDue(session.getCompanyId(), assignedAgentUserId, dueBefore);
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
