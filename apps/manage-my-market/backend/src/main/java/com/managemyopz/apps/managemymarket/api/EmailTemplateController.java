/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for Email template operations.
 */
package com.managemyopz.apps.managemymarket.api;

import com.managemyopz.apps.managemymarket.api.dto.CreateEmailTemplateRequest;
import com.managemyopz.apps.managemymarket.api.dto.UpdateEmailTemplateRequest;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.data.EmailTemplateRepository;
import com.managemyopz.apps.managemymarket.domain.EmailTemplate;
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
 * Controller exposing Email template endpoints.
 */
@RestController
@RequestMapping(ManageMyMarketConstants.API_PREFIX + "/email-templates")
public class EmailTemplateController {

    private final EmailTemplateRepository templateRepository;

    public EmailTemplateController(EmailTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @PostMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "c")
    public ResponseEntity<ApiEnvelope<EmailTemplate>> create(
        @RequestBody CreateEmailTemplateRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String id = UUID.randomUUID().toString();
        EmailTemplate template = new EmailTemplate(
            id,
            session.getCompanyId(),
            body.templateName(),
            body.subject(),
            body.bodyHtml(),
            body.bodyText(),
            Instant.now(),
            Instant.now()
        );
        templateRepository.insert(template);
        return ResponseEntity.ok(ApiEnvelope.ok(template, correlationId(request)));
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
            Optional<EmailTemplate> template = templateRepository.findById(id, session.getCompanyId());
            return ResponseEntity.ok(ApiEnvelope.ok(template.orElse(null), correlationId(request)));
        }

        List<EmailTemplate> list = templateRepository.listByCompany(session.getCompanyId());
        return ResponseEntity.ok(ApiEnvelope.ok(list, correlationId(request)));
    }

    @PutMapping
    @RequiresPermission(module = ManageMyMarketConstants.MODULE_ID, feature = ManageMyMarketConstants.FEATURE_CAMPAIGN, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Boolean>>> update(
        @RequestBody UpdateEmailTemplateRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        EmailTemplate template = new EmailTemplate(
            body.id(),
            session.getCompanyId(),
            body.templateName(),
            body.subject(),
            body.bodyHtml(),
            body.bodyText(),
            null,
            Instant.now()
        );
        templateRepository.update(template);
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
