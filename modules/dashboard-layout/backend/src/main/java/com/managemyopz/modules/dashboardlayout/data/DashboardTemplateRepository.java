/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplate;
import com.managemyopz.modules.dashboardlayout.domain.DashboardTemplateWidget;

import java.util.List;
import java.util.Optional;

public interface DashboardTemplateRepository {
    List<DashboardTemplate> listByApp(String appKey);
    Optional<DashboardTemplate> findByKey(String templateKey);
    void upsert(DashboardTemplate template);
    List<DashboardTemplateWidget> listWidgets(String templateKey);
    void replaceWidgets(String templateKey, List<DashboardTemplateWidget> widgets);
}
