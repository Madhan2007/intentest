/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Agent marketing profile operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.UpdateAgentProfileRequest;
import com.managemyopz.apps.managemymarket.application.AgentProfileService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.AgentProfile;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controller exposing Agent profile endpoints under /api/v1/opzhub/manage-my-market/agent-profiles.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/agent-profiles")
public class AgentProfileController {

    private final AgentProfileService agentProfileService;

    public AgentProfileController(AgentProfileService agentProfileService) {
        this.agentProfileService = agentProfileService;
    }

    @GetMapping("/{userId}")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_AGENT, action = "v")
    public ResponseEntity<ApiEnvelope<AgentProfile>> getProfile(
        @PathVariable("userId") String userId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // ABAC: self or admin
        if (!userId.equals(session.getUserId()) && !hasAdminRole(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<AgentProfile> profile = agentProfileService.getProfileByUserId(userId);
        return ResponseEntity.ok(ApiEnvelope.ok(profile.orElse(null), correlationId(request)));
    }

    @PutMapping("/{userId}")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_AGENT, action = "u")
    public ResponseEntity<ApiEnvelope<AgentProfile>> updateProfile(
        @PathVariable("userId") String userId,
        @RequestBody UpdateAgentProfileRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // ABAC: self or admin
        if (!userId.equals(session.getUserId()) && !hasAdminRole(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        AgentProfile profile = agentProfileService.upsertProfile(
            userId,
            session.getCompanyId(),
            body.marketingType(),
            body.linkedPersonId()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(profile, correlationId(request)));
    }

    private static boolean hasAdminRole(SessionAuthentication session) {
        return session.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().contains("market_admin") || a.getAuthority().contains("admin"));
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
