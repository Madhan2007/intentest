/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.web;

import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.data.client.DataClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/v1/opzhub/health — kernel liveness (doc 04 §4). */
@RestController
public class HealthController {

    private final DataClient dataClient;
    private final CacheClient cacheClient;

    public HealthController(DataClient dataClient, CacheClient cacheClient) {
        this.dataClient = dataClient;
        this.cacheClient = cacheClient;
    }

    @GetMapping("/api/v1/opzhub/health")
    public ApiEnvelope<Map<String, Object>> health(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("db", dataClient.ping() ? "up" : "down");
        data.put("cache", cacheClient.ping() ? "up" : "down");
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return ApiEnvelope.ok(data, correlationId == null ? "" : correlationId.toString());
    }
}
