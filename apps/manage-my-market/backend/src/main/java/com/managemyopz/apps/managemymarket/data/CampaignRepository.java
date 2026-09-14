/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Campaign;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Marketing campaigns.
 */
public interface CampaignRepository {

    void insert(Campaign campaign);

    void update(Campaign campaign);

    List<Campaign> listPaged(String companyId, String status, int limit, int offset);

    Optional<Campaign> findById(String id, String companyId);
}
