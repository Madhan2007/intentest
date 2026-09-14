/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Referrer and Reward operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.CreateReferralRewardRequest;
import com.managemyopz.apps.managemymarket.api.dto.CreateReferrerRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateReferralRewardRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateReferrerRequest;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.application.ReferrerService;
import com.managemyopz.apps.managemymarket.domain.ReferralReward;
import com.managemyopz.apps.managemymarket.domain.Referrer;
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
 * Controller exposing Referrer and Referral Reward endpoints under /api/v1/opzhub/manage-my-market/referrers.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/referrers")
public class ReferrerController {

    private final ReferrerService referrerService;

    public ReferrerController(ReferrerService referrerService) {
        this.referrerService = referrerService;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "c")
    public ResponseEntity<ApiEnvelope<Referrer>> create(
        @RequestBody CreateReferrerRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Referrer referrer = referrerService.createReferrer(
            session.getCompanyId(),
            body.referrerName(),
            body.email(),
            body.phone()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(referrer, correlationId(request)));
    }

    @PostMapping("/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> read(
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "status", required = false) String status,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (id != null && !id.isBlank()) {
            Optional<Referrer> referrer = referrerService.getById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(referrer.orElse(null), correlationId(request)));
        }

        List<Referrer> list = referrerService.listReferrers(session.getCompanyId(), status);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateReferrerRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        referrerService.updateReferrer(
            body.id(),
            session.getCompanyId(),
            body.referrerName(),
            body.email(),
            body.phone(),
            body.status()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @PostMapping("/{id}/rewards")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "c")
    public ResponseEntity<ApiEnvelope<ReferralReward>> createReward(
        @PathVariable("id") String referrerId,
        @RequestBody CreateReferralRewardRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        ReferralReward reward = referrerService.createReward(
            session.getCompanyId(),
            referrerId,
            body.leadId(),
            body.amount(),
            body.payoutDetails()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(reward, correlationId(request)));
    }

    @PostMapping("/{id}/rewards/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "v")
    public ResponseEntity<ApiEnvelope<List<ReferralReward>>> readRewards(
        @PathVariable("id") String referrerId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<ReferralReward> list = referrerService.listRewardsByReferrer(referrerId);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping("/{id}/rewards")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_REFERRAL, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> updateReward(
        @PathVariable("id") String referrerId,
        @RequestBody UpdateReferralRewardRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        referrerService.updateRewardStatus(body.id(), referrerId, body.status(), body.payoutDetails());
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
