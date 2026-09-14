/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.modules.dashboardlayout.domain.DashboardLayout;

import java.util.Optional;
import java.util.UUID;

public interface DashboardLayoutRepository {
    Optional<DashboardLayout> findByCompanyAppRole(UUID companyId, String appKey, String roleKey);
    void upsert(DashboardLayout layout);
}
