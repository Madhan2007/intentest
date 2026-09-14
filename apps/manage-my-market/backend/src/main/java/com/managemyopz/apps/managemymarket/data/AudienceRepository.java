/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Audience;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Audience segments.
 */
public interface AudienceRepository {

    void insert(Audience audience);

    void update(Audience audience);

    List<Audience> listByCompany(String companyId);

    Optional<Audience> findById(String id, String companyId);
}
