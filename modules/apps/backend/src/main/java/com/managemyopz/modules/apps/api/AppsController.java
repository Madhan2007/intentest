/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Dedicated company application catalog API (not generic CRUD).
 */
package com.managemyopz.modules.apps.api;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import com.managemyopz.modules.apps.api.dto.ApplicationActionRequest;
import com.managemyopz.modules.apps.api.dto.ApplicationFavouriteRequest;
import com.managemyopz.modules.apps.api.dto.CompanyApplicationsResponse;
import com.managemyopz.modules.apps.application.CompanyApplicationService;
import com.managemyopz.modules.apps.domain.CompanyApplicationView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists the product catalog with this company's license state, and applies
 * license/install transitions. Company comes from the session, never the client.
 */
@RestController
@RequestMapping("/api/v1/opzhub/apps")
public class AppsController {

    private final CompanyApplicationService companyApplicationService;

    /**
     * Creates the applications API.
     *
     * @param companyApplicationService company-scoped catalog service
     */
    public AppsController(CompanyApplicationService companyApplicationService) {
        this.companyApplicationService = companyApplicationService;
    }

    /**
     * Returns every catalog application and this company's license state.
     *
     * @param authentication current security principal
     * @param request incoming HTTP request
     * @return catalog views
     */
    @GetMapping
    public ResponseEntity<ApiEnvelope<CompanyApplicationsResponse>> list(
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompanyApplicationsResponse payload =
            new CompanyApplicationsResponse(companyApplicationService.listForSession(session));
        return ResponseEntity.ok(ApiEnvelope.ok(payload, correlationId(request)));
    }

    /**
     * Marks a catalog application licensed for the session company.
     *
     * @param requestBody catalog application id
     * @param authentication current security principal
     * @param request incoming HTTP request
     * @return updated application view
     */
    @PostMapping("/license")
    public ResponseEntity<ApiEnvelope<CompanyApplicationView>> license(
        @RequestBody ApplicationActionRequest requestBody,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompanyApplicationView view = companyApplicationService.license(session, applicationId(requestBody));
        return ResponseEntity.ok(ApiEnvelope.ok(view, correlationId(request)));
    }

    /**
     * Installs a licensed application so it appears on the company dashboard.
     *
     * @param requestBody catalog application id
     * @param authentication current security principal
     * @param request incoming HTTP request
     * @return updated application view
     */
    @PostMapping("/install")
    public ResponseEntity<ApiEnvelope<CompanyApplicationView>> install(
        @RequestBody ApplicationActionRequest requestBody,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompanyApplicationView view = companyApplicationService.install(session, applicationId(requestBody));
        return ResponseEntity.ok(ApiEnvelope.ok(view, correlationId(request)));
    }

    /**
     * Uninstalls an installed application back to licensed for this company.
     *
     * @param requestBody catalog application id
     * @param authentication current security principal
     * @param request incoming HTTP request
     * @return updated application view
     */
    @PostMapping("/uninstall")
    public ResponseEntity<ApiEnvelope<CompanyApplicationView>> uninstall(
        @RequestBody ApplicationActionRequest requestBody,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        CompanyApplicationView view = companyApplicationService.uninstall(session, applicationId(requestBody));
        return ResponseEntity.ok(ApiEnvelope.ok(view, correlationId(request)));
    }

    /**
     * Marks or unmarks an installed application as a dashboard favourite.
     *
     * @param requestBody catalog application id and favourite flag
     * @param authentication current security principal
     * @param request incoming HTTP request
     * @return updated application view
     */
    @PostMapping("/favourite")
    public ResponseEntity<ApiEnvelope<CompanyApplicationView>> favourite(
        @RequestBody ApplicationFavouriteRequest requestBody,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (requestBody == null || requestBody.applicationId() == null || requestBody.applicationId().isBlank()) {
            throw DataClientException.notFound("Unknown application.");
        }
        boolean favourite = Boolean.TRUE.equals(requestBody.favourite());
        CompanyApplicationView view =
            companyApplicationService.setFavourite(session, requestBody.applicationId(), favourite);
        return ResponseEntity.ok(ApiEnvelope.ok(view, correlationId(request)));
    }

    private static String applicationId(ApplicationActionRequest requestBody) {
        if (requestBody == null || requestBody.applicationId() == null || requestBody.applicationId().isBlank()) {
            throw DataClientException.notFound("Unknown application.");
        }
        return requestBody.applicationId();
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        if (authentication instanceof SessionAuthentication session) {
            return session;
        }
        return null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }

}
