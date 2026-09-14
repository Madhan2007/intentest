/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Audience segment operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.CreateAudienceRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateAudienceRequest;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.data.AudienceRepository;
import com.managemyopz.apps.managemymarket.domain.Audience;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller exposing Audience segment endpoints.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/audiences")
public class AudienceController {

    private final AudienceRepository audienceRepository;

    public AudienceController(AudienceRepository audienceRepository) {
        this.audienceRepository = audienceRepository;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "c")
    public ResponseEntity<ApiEnvelope<Audience>> create(
        @RequestBody CreateAudienceRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String id = UUID.randomUUID().toString();
        Audience audience = new Audience(
            id,
            session.getCompanyId(),
            body.audienceName(),
            body.description(),
            body.rulesJson(),
            Instant.now(),
            Instant.now()
        );
        audienceRepository.insert(audience);
        return ResponseEntity.ok(ApiEnvelope.ok(audience, correlationId(request)));
    }

    @PostMapping("/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> read(
        @RequestParam(name = "id", required = false) String id,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (id != null && !id.isBlank()) {
            Optional<Audience> audience = audienceRepository.findById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(audience.orElse(null), correlationId(request)));
        }

        List<Audience> list = audienceRepository.listByCompany(session.getCompanyId());
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateAudienceRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Audience audience = new Audience(
            body.id(),
            session.getCompanyId(),
            body.audienceName(),
            body.description(),
            body.rulesJson(),
            null,
            Instant.now()
        );
        audienceRepository.update(audience);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
