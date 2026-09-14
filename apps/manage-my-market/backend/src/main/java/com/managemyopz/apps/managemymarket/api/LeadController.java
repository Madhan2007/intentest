/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Lead operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.AddLeadActivityRequest;
import com.managemyopz.apps.managemymarket.api.dto.AssignLeadRequest;
import com.managemyopz.apps.managemymarket.api.dto.CreateLeadRequest;
import com.managemyopz.apps.managemymarket.api.dto.DisqualifyLeadRequest;
import com.managemyopz.apps.managemymarket.api.dto.LeadCodeResponse;
import com.managemyopz.apps.managemymarket.api.dto.ReadLeadsRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateLeadRequest;
import com.managemyopz.apps.managemymarket.application.LeadActivityService;
import com.managemyopz.apps.managemymarket.application.LeadAssignmentService;
import com.managemyopz.apps.managemymarket.application.LeadCodeSequenceService;
import com.managemyopz.apps.managemymarket.application.LeadService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.Lead;
import com.managemyopz.apps.managemymarket.domain.LeadActivity;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller exposing Lead endpoints under /api/v1/opzhub/manage-my-market/leads.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/leads")
public class LeadController {

    private final LeadService leadService;
    private final LeadAssignmentService assignmentService;
    private final LeadActivityService activityService;
    private final LeadCodeSequenceService codeSequenceService;

    public LeadController(LeadService leadService,
                          LeadAssignmentService assignmentService,
                          LeadActivityService activityService,
                          LeadCodeSequenceService codeSequenceService) {
        this.leadService = leadService;
        this.assignmentService = assignmentService;
        this.activityService = activityService;
        this.codeSequenceService = codeSequenceService;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "c")
    public ResponseEntity<ApiEnvelope<Lead>> create(
        @RequestBody CreateLeadRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Lead lead = leadService.createLead(
            session.getCompanyId(),
            body.displayName(),
            body.leadSourceType(),
            body.firstName(),
            body.lastName(),
            body.companyName(),
            body.email(),
            body.phone(),
            body.sourceDetail(),
            body.estimatedValue(),
            body.priority(),
            body.referrerId(),
            body.ownerUserId(),
            session.getUserId(),
            body.notes()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(lead, correlationId(request)));
    }

    @PostMapping("/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> read(
        @RequestBody(required = false) ReadLeadsRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (body != null && body.id() != null && !body.id().isBlank()) {
            Optional<Lead> lead = leadService.findById(body.id(), session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(lead.orElse(null), correlationId(request)));
        }

        int limit = (body != null && body.limit() != null) ? body.limit() : 50;
        int offset = (body != null && body.offset() != null) ? body.offset() : 0;
        String status = body != null ? body.status() : null;
        String sourceType = body != null ? body.leadSourceType() : null;
        String ownerUserId = body != null ? body.ownerUserId() : null;

        List<Lead> leads = leadService.listPaged(session.getCompanyId(), status, sourceType, ownerUserId, limit, offset);
        return ResponseEntity.ok(ApiEnvelope.ok(leads, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateLeadRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        leadService.updateLead(
            body.id(),
            session.getCompanyId(),
            body.displayName(),
            body.firstName(),
            body.lastName(),
            body.companyName(),
            body.email(),
            body.phone(),
            body.status(),
            body.sourceDetail(),
            body.estimatedValue(),
            body.priority(),
            body.referrerId(),
            body.notes(),
            session.getUserId()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @DeleteMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "d")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> disqualify(
        @RequestBody DisqualifyLeadRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        leadService.disqualify(body.id(), session.getCompanyId(), body.reason(), session.getUserId());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("disqualified", true), correlationId(request)));
    }

    @GetMapping("/code/preview")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<LeadCodeResponse>> previewCode(
        @RequestParam(name = "prefix", required = false) String prefix,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String preview = codeSequenceService.previewNextCode(session.getCompanyId(), prefix);
        return ResponseEntity.ok(ApiEnvelope.ok(new LeadCodeResponse(preview), correlationId(request)));
    }

    @PostMapping("/code/reserve")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "c")
    public ResponseEntity<ApiEnvelope<LeadCodeResponse>> reserveCode(
        @RequestParam(name = "prefix", required = false) String prefix,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String code = codeSequenceService.reserveNextCode(session.getCompanyId(), prefix);
        return ResponseEntity.ok(ApiEnvelope.ok(new LeadCodeResponse(code), correlationId(request)));
    }

    @PutMapping("/{id}/assign")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> assign(
        @PathVariable("id") String id,
        @RequestBody AssignLeadRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        assignmentService.assignOwner(id, session.getCompanyId(), body.newOwnerUserId(), session.getUserId(), body.transferReason());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("assigned", true), correlationId(request)));
    }

    @PostMapping("/{id}/activity")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> addActivity(
        @PathVariable("id") String id,
        @RequestBody AddLeadActivityRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        activityService.logActivity(id, body.activityType(), body.title(), body.description(), null, null, null, null, session.getUserId());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("created", true), correlationId(request)));
    }

    @PostMapping("/{id}/activity/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_LEAD, action = "v")
    public ResponseEntity<ApiEnvelope<List<LeadActivity>>> readActivity(
        @PathVariable("id") String id,
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<LeadActivity> list = activityService.listTimeline(id, limit, offset);
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
