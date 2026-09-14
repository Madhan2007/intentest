/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Referrer;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Referrers.
 */
public interface ReferrerRepository {

    void insert(Referrer referrer);

    void update(Referrer referrer);

    List<Referrer> listByCompany(String companyId, String status);

    Optional<Referrer> findById(String id, String companyId);

    void recomputeAggregates(String referrerId);
}
