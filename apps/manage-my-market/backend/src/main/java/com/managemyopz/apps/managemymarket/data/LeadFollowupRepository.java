/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadFollowup;

import java.time.Instant;
import java.util.List;

/**
 * Data access interface for Lead follow-up tasks.
 */
public interface LeadFollowupRepository {

    void insert(LeadFollowup followup);

    void update(LeadFollowup followup);

    List<LeadFollowup> listByLead(String leadId);

    List<LeadFollowup> listDue(String companyId, String assignedAgentUserId, Instant dueBefore);
}
