/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Journey;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Journey nurture workflows.
 */
public interface JourneyRepository {

    void insert(Journey journey);

    void update(Journey journey);

    List<Journey> listByCompany(String companyId);

    Optional<Journey> findById(String id, String companyId);
}
