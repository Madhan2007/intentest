/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Campaign operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.AddChannelRequest;
import com.managemyopz.apps.managemymarket.api.dto.CreateCampaignRequest;
import com.managemyopz.apps.managemymarket.api.dto.EnrollRecipientsRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateCampaignRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateRecipientStatusRequest;
import com.managemyopz.apps.managemymarket.application.CampaignService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.Campaign;
import com.managemyopz.apps.managemymarket.domain.CampaignChannel;
import com.managemyopz.apps.managemymarket.domain.CampaignRecipient;
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
 * Controller exposing Campaign endpoints under /api/v1/opzhub/manage-my-market/campaigns.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "c")
    public ResponseEntity<ApiEnvelope<Campaign>> create(
        @RequestBody CreateCampaignRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Campaign campaign = campaignService.createCampaign(
            session.getCompanyId(),
            body.campaignName(),
            body.status(),
            body.startDate(),
            body.endDate(),
            body.budget(),
            session.getUserId()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(campaign, correlationId(request)));
    }

    @PostMapping("/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> read(
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (id != null && !id.isBlank()) {
            Optional<Campaign> campaign = campaignService.getById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(campaign.orElse(null), correlationId(request)));
        }

        List<Campaign> list = campaignService.listPaged(session.getCompanyId(), status, limit, offset);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateCampaignRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        campaignService.updateCampaign(
            body.id(),
            session.getCompanyId(),
            body.campaignName(),
            body.status(),
            body.startDate(),
            body.endDate(),
            body.budget()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @PostMapping("/{id}/channels")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<CampaignChannel>> addChannel(
        @PathVariable("id") String campaignId,
        @RequestBody AddChannelRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        CampaignChannel channel = campaignService.addChannel(campaignId, body.channelType(), body.budget(), body.spend());
        return ResponseEntity.ok(ApiEnvelope.ok(channel, correlationId(request)));
    }

    @PostMapping("/{id}/channels/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<List<CampaignChannel>>> readChannels(
        @PathVariable("id") String campaignId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<CampaignChannel> list = campaignService.listChannels(campaignId);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/{id}/recipients")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> enrollRecipients(
        @PathVariable("id") String campaignId,
        @RequestBody EnrollRecipientsRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        campaignService.enrollRecipients(campaignId, body.leadIds());
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("enrolled", true), correlationId(request)));
    }

    @PostMapping("/{id}/recipients/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "v")
    public ResponseEntity<ApiEnvelope<List<CampaignRecipient>>> readRecipients(
        @PathVariable("id") String campaignId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "limit", defaultValue = "50") int limit,
        @RequestParam(name = "offset", defaultValue = "0") int offset,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<CampaignRecipient> list = campaignService.listRecipients(campaignId, status, limit, offset);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping("/{id}/recipients")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> updateRecipientStatus(
        @PathVariable("id") String campaignId,
        @RequestBody UpdateRecipientStatusRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        campaignService.updateRecipientStatus(campaignId, body.leadId(), body.status());
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
