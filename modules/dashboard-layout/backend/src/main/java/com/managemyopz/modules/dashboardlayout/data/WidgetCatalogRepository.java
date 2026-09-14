/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.modules.dashboardlayout.domain.WidgetCatalogItem;

import java.util.List;

public interface WidgetCatalogRepository {
    List<WidgetCatalogItem> listByApp(String appKey);
    void upsert(WidgetCatalogItem item);
}
