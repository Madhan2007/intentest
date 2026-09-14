/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Lead;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Lead records.
 */
public interface LeadRepository {

    Optional<Lead> findById(String id, String companyId);

    Optional<Lead> findByCompanyAndCode(String companyId, String leadCode);

    List<Lead> listPaged(String companyId, String status, String leadSourceType, String ownerUserId, int limit, int offset);

    void insert(Lead lead);

    void update(Lead lead);

    void disqualify(String id, String companyId, String reason);
}
