/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.dashboardlayout.data;

import com.managemyopz.modules.dashboardlayout.domain.DashboardLayoutUser;

import java.util.Optional;
import java.util.UUID;

public interface DashboardLayoutUserRepository {
    Optional<DashboardLayoutUser> findByUserCompanyApp(UUID userId, UUID companyId, String appKey);
    void upsert(DashboardLayoutUser userLayout);
    void delete(UUID userId, UUID companyId, String appKey);
}
