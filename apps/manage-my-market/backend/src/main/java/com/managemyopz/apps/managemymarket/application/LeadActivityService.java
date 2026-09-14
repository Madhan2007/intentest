/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadActivityRepository;
import com.managemyopz.apps.managemymarket.domain.LeadActivity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing the lead timeline / activity stream.
 */
public class LeadActivityService {

    private final LeadActivityRepository activityRepository;

    public LeadActivityService(LeadActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void logActivity(String leadId, String activityType, String title, String description,
                            String oldValue, String newValue, String callLogId, String followupId,
                            String performedByUserId) {
        LeadActivity activity = new LeadActivity(
            UUID.randomUUID().toString(),
            leadId,
            activityType != null ? activityType : ManageMyMarketConstants.ACTIVITY_TYPE_NOTE,
            title,
            description,
            oldValue,
            newValue,
            callLogId,
            followupId,
            performedByUserId,
            Instant.now()
        );
        activityRepository.insert(activity);
    }

    public List<LeadActivity> listTimeline(String leadId, int limit, int offset) {
        int safeLimit = Math.clamp(limit, 1, 100);
        int safeOffset = Math.max(0, offset);
        return activityRepository.listByLeadPaged(leadId, safeLimit, safeOffset);
    }
}
