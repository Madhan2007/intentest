/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadFollowupRepository;
import com.managemyopz.apps.managemymarket.domain.LeadFollowup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for scheduled callback/follow-up management.
 */
public class LeadFollowupService {

    private final LeadFollowupRepository followupRepository;
    private final LeadActivityService activityService;

    public LeadFollowupService(LeadFollowupRepository followupRepository, LeadActivityService activityService) {
        this.followupRepository = followupRepository;
        this.activityService = activityService;
    }

    public LeadFollowup createFollowup(String leadId, String reason, Instant scheduledAt,
                                       String assignedAgentUserId, String priority, String notes) {
        String id = UUID.randomUUID().toString();
        LeadFollowup followup = new LeadFollowup(
            id,
            leadId,
            reason,
            scheduledAt != null ? scheduledAt : Instant.now(),
            assignedAgentUserId,
            priority != null ? priority : ManageMyMarketConstants.PRIORITY_WARM,
            ManageMyMarketConstants.FOLLOWUP_STATUS_SCHEDULED,
            notes,
            Instant.now(),
            Instant.now()
        );
        followupRepository.insert(followup);

        activityService.logActivity(
            leadId,
            ManageMyMarketConstants.ACTIVITY_TYPE_FOLLOWUP,
            "Follow-up Scheduled",
            reason != null ? reason : "Scheduled callback",
            null,
            null,
            null,
            id,
            assignedAgentUserId
        );

        return followup;
    }

    public void updateFollowup(String id, String leadId, String reason, Instant scheduledAt,
                               String assignedAgentUserId, String priority, String status, String notes) {
        LeadFollowup followup = new LeadFollowup(
            id,
            leadId,
            reason,
            scheduledAt,
            assignedAgentUserId,
            priority,
            status,
            notes,
            null,
            Instant.now()
        );
        followupRepository.update(followup);

        if (ManageMyMarketConstants.FOLLOWUP_STATUS_COMPLETED.equalsIgnoreCase(status)) {
            activityService.logActivity(
                leadId,
                ManageMyMarketConstants.ACTIVITY_TYPE_FOLLOWUP,
                "Follow-up Completed",
                notes != null ? notes : "Completed follow-up",
                null,
                status,
                null,
                id,
                assignedAgentUserId
            );
        }
    }

    public List<LeadFollowup> listByLead(String leadId) {
        return followupRepository.listByLead(leadId);
    }

    public List<LeadFollowup> listDue(String companyId, String assignedAgentUserId, Instant dueBefore) {
        Instant effectiveDueBefore = dueBefore != null ? dueBefore : Instant.now();
        return followupRepository.listDue(companyId, assignedAgentUserId, effectiveDueBefore);
    }
}
