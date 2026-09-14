/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.web;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.module.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/v1/opzhub/meta/modules and /meta/auth — installed-pack-only
 * bootstrap info (doc 04 §4, doc 18 §2.5). Never the full vendor catalog.
 */
@RestController
public class MetaController {

    private final ModuleCatalog moduleCatalog;
    private final PlatformProperties platformProperties;

    public MetaController(ModuleCatalog moduleCatalog, PlatformProperties platformProperties) {
        this.moduleCatalog = moduleCatalog;
        this.platformProperties = platformProperties;
    }

    @GetMapping("/api/v1/opzhub/meta/modules")
    public ApiEnvelope<Map<String, Object>> modules(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("modules", moduleCatalog.enabledModuleIds());
        return ApiEnvelope.ok(data, correlationId(request));
    }

    @GetMapping("/api/v1/opzhub/meta/auth")
    public ApiEnvelope<Map<String, Object>> auth(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("methods", platformProperties.getSecurity().getAuth().getMethods());
        return ApiEnvelope.ok(data, correlationId(request));
    }

    /** Browser-safe runtime config — never db/cache secrets (doc 08 §6). */
    @GetMapping("/api/v1/opzhub/meta/config")
    public ApiEnvelope<Map<String, Object>> config(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("solutionId", platformProperties.getSolution().getId());
        data.put("profile", platformProperties.getSolution().getProfile());
        data.put("modules", moduleCatalog.enabledModuleIds());
        data.put("wsPath", "/ws/opzhub");
        data.put("apiOpzhub", "/api/v1/opzhub");
        data.put("apiAi", "/api/v1/ai");
        data.put("auth", Map.of("methods", platformProperties.getSecurity().getAuth().getMethods()));
        data.put("gui", Map.of("mode", "lite", "allowUserChoice", true));
        return ApiEnvelope.ok(data, correlationId(request));
    }

    private String correlationId(HttpServletRequest request) {
        Object v = request.getAttribute(CorrelationFilter.MDC_KEY);
        return v == null ? "" : v.toString();
    }
}
