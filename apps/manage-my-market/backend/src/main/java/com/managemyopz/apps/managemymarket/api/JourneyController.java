/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Journey operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.CreateJourneyRequest;
import com.managemyopz.apps.managemymarket.api.dto.EnrollJourneyLeadRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateJourneyRequest;
import com.managemyopz.apps.managemymarket.application.JourneyService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.Journey;
import com.managemyopz.apps.managemymarket.domain.JourneyEnrollment;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * Controller exposing Journey endpoints under /api/v1/opzhub/manage-my-market/journeys.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/journeys")
public class JourneyController {

    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "c")
    public ResponseEntity<ApiEnvelope<Journey>> create(
        @RequestBody CreateJourneyRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Journey journey = journeyService.createJourney(
            session.getCompanyId(),
            body.journeyName(),
            body.status(),
            body.triggerType(),
            body.flowDefinitionJson()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(journey, correlationId(request)));
    }

    @PostMapping("/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> read(
        @RequestParam(name = "id", required = false) String id,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (id != null && !id.isBlank()) {
            Optional<Journey> journey = journeyService.getById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(journey.orElse(null), correlationId(request)));
        }

        List<Journey> list = journeyService.listByCompany(session.getCompanyId());
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateJourneyRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        journeyService.updateJourney(
            body.id(),
            session.getCompanyId(),
            body.journeyName(),
            body.status(),
            body.triggerType(),
            body.flowDefinitionJson()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @PostMapping("/{id}/enroll")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "u")
    public ResponseEntity<ApiEnvelope<JourneyEnrollment>> enrollLead(
        @PathVariable("id") String journeyId,
        @RequestBody EnrollJourneyLeadRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        JourneyEnrollment enrollment = journeyService.enrollLead(journeyId, body.leadId(), body.initialNodeId());
        return ResponseEntity.ok(ApiEnvelope.ok(enrollment, correlationId(request)));
    }

    @PostMapping("/{id}/enroll/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_JOURNEY, action = "v")
    public ResponseEntity<ApiEnvelope<List<JourneyEnrollment>>> readEnrollments(
        @PathVariable("id") String journeyId,
        @RequestParam(name = "status", required = false) String status,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<JourneyEnrollment> list = journeyService.listEnrollments(journeyId, status);
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
