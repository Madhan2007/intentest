/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadActivity;

import java.util.List;

/**
 * Data access interface for Lead activity timeline.
 */
public interface LeadActivityRepository {

    void insert(LeadActivity activity);

    List<LeadActivity> listByLeadPaged(String leadId, int limit, int offset);
}
