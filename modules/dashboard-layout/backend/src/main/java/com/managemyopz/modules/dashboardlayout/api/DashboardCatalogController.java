/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Catalog and template endpoints for dashboard layout.
 */
package com.managemyopz.modules.dashboardlayout.api;

import com.managemyopz.kernel.security.RequiresPermission;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import com.managemyopz.modules.dashboardlayout.data.DashboardTemplateRepository;
import com.managemyopz.modules.dashboardlayout.data.WidgetCatalogRepository;
import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutConstants;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplate;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplateWidget;
import com.managemyopz.modules.dashboardlayout.domain.WidgetCatalogItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(DashboardLayoutConstants.API_PREFIX)
public class DashboardCatalogController {

    private final WidgetCatalogRepository widgetCatalogRepository;
    private final DashboardTemplateRepository templateRepository;

    public DashboardCatalogController(WidgetCatalogRepository widgetCatalogRepository,
                                      DashboardTemplateRepository templateRepository) {
        this.widgetCatalogRepository = widgetCatalogRepository;
        this.templateRepository = templateRepository;
    }

    @GetMapping("/catalog")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_CATALOG, action = "v")
    public ResponseEntity<ApiEnvelope<List<WidgetCatalogItem>>> listCatalog(
        @RequestParam(name = "app_key", defaultValue = "*") String appKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<WidgetCatalogItem> items = widgetCatalogRepository.listByApp(appKey);
        return ResponseEntity.ok(ApiEnvelope.ok(items, correlationId(request)));
    }

    @GetMapping("/templates")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_TEMPLATES, action = "v")
    public ResponseEntity<ApiEnvelope<List<DashboardTemplate>>> listTemplates(
        @RequestParam(name = "app_key", defaultValue = "*") String appKey,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<DashboardTemplate> templates = templateRepository.listByApp(appKey);
        return ResponseEntity.ok(ApiEnvelope.ok(templates, correlationId(request)));
    }

    @GetMapping("/templates/{key}/widgets")
    @RequiresPermission(module = DashboardLayoutConstants.MODULE_ID, feature = DashboardLayoutConstants.FEATURE_TEMPLATES, action = "v")
    public ResponseEntity<ApiEnvelope<List<DashboardTemplateWidget>>> listTemplateWidgets(
        @PathVariable("key") String key,
        Authentication authentication,
        HttpServletRequest request
    ) {
        SessionAuthentication session = requireSession(authentication);
        if (session == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<DashboardTemplateWidget> widgets = templateRepository.listWidgets(key);
        return ResponseEntity.ok(ApiEnvelope.ok(widgets, correlationId(request)));
    }

    private static SessionAuthentication requireSession(Authentication authentication) {
        return authentication instanceof SessionAuthentication session ? session : null;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationFilter.MDC_KEY);
        return value == null ? "" : value.toString();
    }
}
