/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.admin.api;

import com.managemyopz.kernel.module.ConditionalOnModule;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Proves the admin module is wired end-to-end; real settings screens arrive later. */
@RestController
@ConditionalOnModule("admin")
public class AdminPingController {

    @GetMapping("/api/v1/opzhub/admin/ping")
    public ApiEnvelope<Map<String, Object>> ping(HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return ApiEnvelope.ok(Map.of("module", "admin", "status", "placeholder"),
            correlationId == null ? "" : correlationId.toString());
    }
}
