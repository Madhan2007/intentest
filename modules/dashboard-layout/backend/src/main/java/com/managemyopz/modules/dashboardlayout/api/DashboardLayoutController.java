/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: REST controller for role layouts and user personalizations.
 */
package com.managemyopz.modules.dashboardlayout.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import com.managemyopz.modules.dashboardlayout.api.dto.ApplyTemplateRequest;
import com.managemyopz.modules.dashboardlayout.api.dto.SaveLayoutRequest;
import com.managemyopz.modules.dashboardlayout.api.dto.SaveUserLayoutRequest;
import com.managemyopz.modules.dashboardlayout.application.DashboardLayoutService;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayout;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping(DashboardLayoutConstants.API_PREFIX)
public class DashboardLayoutController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DashboardLayoutService layoutService;
    private final ObjectMapper objectMapper;

    public DashboardLayoutController(DashboardLayoutService layoutService, ObjectMapper objectMapper) {
        this.layoutService = layoutService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/layout")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_ROLE_LAYOUT, action = "v")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> getRoleLayout(
        @RequestParam("app_key") String appKey,
        @RequestParam("role_key") String roleKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Optional<DashboardLayout> layout = layoutService.getRoleLayout(session.getCompanyId(), appKey, roleKey);
        if (layout.isEmpty()) {
            return ResponseEntity.ok(ApiEnvelope.ok(Map.of(), correlationId(request)));
        }

        try {
            Map<String, Object> data = objectMapper.readValue(layout.get().layoutDataJson(), MAP_TYPE);
            return ResponseEntity.ok(ApiEnvelope.ok(data, correlationId(request)));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiEnvelope.ok(Map.of(), correlationId(request)));
        }
    }

    @PutMapping("/layout")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_ROLE_LAYOUT, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> saveRoleLayout(
        @RequestBody SaveLayoutRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            String json = objectMapper.writeValueAsString(body.layoutData() != null ? body.layoutData() : Map.of());
            layoutService.saveRoleLayout(
                    session.getCompanyId(),
                    body.appKey(),
                    body.roleKey(),
                    body.templateKey(),
                    json,
                    session.getUserId()
            );
            return ResponseEntity.ok(ApiEnvelope.ok(Map.of("saved", true), correlationId(request)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/layout/apply-template")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_ROLE_LAYOUT, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> applyTemplate(
        @RequestBody ApplyTemplateRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        layoutService.applyTemplate(
                session.getCompanyId(),
                body.appKey(),
                body.roleKey(),
                body.templateKey(),
                session.getUserId()
        );
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("applied", true), correlationId(request)));
    }

    @GetMapping("/my-layout")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_MY_LAYOUT, action = "v")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> getMyLayout(
        @RequestParam("app_key") String appKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String primaryRole = !session.getRoles().isEmpty() ? session.getRoles().getFirst() : "viewer";
        String layoutJson = layoutService.getMyLayout(session.getUserId(), session.getCompanyId(), appKey, primaryRole);

        try {
            Map<String, Object> data = objectMapper.readValue(layoutJson, MAP_TYPE);
            return ResponseEntity.ok(ApiEnvelope.ok(data, correlationId(request)));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiEnvelope.ok(Map.of(), correlationId(request)));
        }
    }

    @PutMapping("/my-layout")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_MY_LAYOUT, action = "u")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> saveMyLayout(
        @RequestBody SaveUserLayoutRequest body,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            String json = objectMapper.writeValueAsString(body.layoutData() != null ? body.layoutData() : Map.of());
            layoutService.saveMyLayout(session.getUserId(), session.getCompanyId(), body.appKey(), json);
            return ResponseEntity.ok(ApiEnvelope.ok(Map.of("saved", true), correlationId(request)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/my-layout")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_MY_LAYOUT, action = "d")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> resetMyLayout(
        @RequestParam("app_key") String appKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        layoutService.resetMyLayout(session.getUserId(), session.getCompanyId(), appKey);
        return ResponseEntity.ok(ApiEnvelope.ok(Map.of("reset", true), correlationId(request)));
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
