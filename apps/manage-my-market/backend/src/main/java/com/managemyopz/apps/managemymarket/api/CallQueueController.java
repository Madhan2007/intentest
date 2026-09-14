/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Call queue and telemarketing operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.AddCallQueueItemRequest;
import com.managemyopz.apps.managemymarket.api.dto.CreateCallLogRequest;
import com.managemyopz.apps.managemymarket.api.dto.CreateCallQueueRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateCallQueueItemRequest;
import com.managemyopz.apps.managemymarket.application.CallQueueService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.domain.CallLog;
import com.managemyopz.apps.managemymarket.domain.CallQueue;
import com.managemyopz.apps.managemymarket.domain.CallQueueItem;
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
 * Controller exposing Call queue and dialer endpoints.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX)
public class CallQueueController {

    private final CallQueueService callQueueService;

    public CallQueueController(CallQueueService callQueueService) {
        this.callQueueService = callQueueService;
    }

    @PostMapping("/call-queues")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "c")
    public ResponseEntity<ApiEnvelope<CallQueue>> createQueue(
        @RequestBody CreateCallQueueRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        CallQueue queue = callQueueService.createQueue(
            session.getCompanyId(),
            body.queueName(),
            body.description(),
            body.priority() != null ? body.priority() : 0,
            session.getUserId()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(queue, correlationId(request)));
    }

    @PostMapping("/call-queues/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "v")
    public ResponseEntity<ApiEnvelope<Object>> readQueues(
        @RequestParam(name = "id", required = false) String id,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        if (id != null && !id.isBlank()) {
            Optional<CallQueue> queue = callQueueService.getQueueById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(queue.orElse(null), correlationId(request)));
        }

        List<CallQueue> list = callQueueService.listQueues(session.getCompanyId());
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PostMapping("/call-queues/{id}/items")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "c")
    public ResponseEntity<ApiEnvelope<CallQueueItem>> addItem(
        @PathVariable("id") String queueId,
        @RequestBody AddCallQueueItemRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        CallQueueItem item = callQueueService.addItem(
            queueId,
            body.leadId(),
            body.priority(),
            body.assignedAgentUserId() != null ? body.assignedAgentUserId() : session.getUserId(),
            body.nextCallScheduledAt(),
            body.notes()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(item, correlationId(request)));
    }

    @PostMapping("/call-queues/{id}/items/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "v")
    public ResponseEntity<ApiEnvelope<List<CallQueueItem>>> readItems(
        @PathVariable("id") String queueId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "assignedAgentUserId", required = false) String assignedAgentUserId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<CallQueueItem> list = callQueueService.listItems(queueId, status, assignedAgentUserId);
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping("/call-queues/{id}/items/{itemId}")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> updateItem(
        @PathVariable("id") String queueId,
        @PathVariable("itemId") String itemId,
        @RequestBody UpdateCallQueueItemRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        callQueueService.updateItem(
            itemId,
            body.priority(),
            body.status(),
            body.assignedAgentUserId(),
            body.nextCallScheduledAt(),
            body.notes()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("updated", true), correlationId(request)));
    }

    @PostMapping("/calls")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "c")
    public ResponseEntity<ApiEnvelope<CallLog>> recordCall(
        @RequestBody CreateCallLogRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        CallLog callLog = callQueueService.recordCall(
            session.getCompanyId(),
            body.queueId(),
            body.leadId(),
            session.getUserId(),
            body.phoneNumber(),
            body.connectionStatus(),
            body.disposition(),
            body.durationSeconds() != null ? body.durationSeconds() : 0,
            body.recordingUrl(),
            body.notes(),
            body.scheduledFollowupAt()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(callLog, correlationId(request)));
    }

    @PostMapping("/calls/read")
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CALL_QUEUE, action = "v")
    public ResponseEntity<ApiEnvelope<List<CallLog>>> readCalls(
        @RequestParam("leadId") String leadId,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<CallLog> list = callQueueService.listCallsByLead(leadId);
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
