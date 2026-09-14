/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadAssignmentRepository;
import com.managemyopz.apps.managemymarket.data.LeadRepository;
import com.managemyopz.apps.managemymarket.domain.Lead;
import com.managemyopz.apps.managemymarket.domain.LeadAssignment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing lead ownership assignments.
 * Enforces that every owner change writes an audit record in mkt_lead_assignment.
 */
public class LeadAssignmentService {

    private final LeadRepository leadRepository;
    private final LeadAssignmentRepository assignmentRepository;
    private final LeadActivityService activityService;

    public LeadAssignmentService(LeadRepository leadRepository,
                                 LeadAssignmentRepository assignmentRepository,
                                 LeadActivityService activityService) {
        this.leadRepository = leadRepository;
        this.assignmentRepository = assignmentRepository;
        this.activityService = activityService;
    }

    public void assignOwner(String leadId, String companyId, String newOwnerUserId, String assignedByUserId, String transferReason) {
        Optional<Lead> existingOpt = leadRepository.findById(leadId, companyId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Lead not found: " + leadId);
        }
        Lead existing = existingOpt.get();
        String oldOwner = existing.ownerUserId();

        // 1. Insert assignment history record
        LeadAssignment assignment = new LeadAssignment(
            UUID.randomUUID().toString(),
            leadId,
            assignedByUserId,
            newOwnerUserId,
            transferReason,
            Instant.now()
        );
        assignmentRepository.insert(assignment);

        // 2. Update lead owner
        Lead updated = new Lead(
            existing.id(),
            existing.companyId(),
            existing.leadCode(),
            existing.leadSourceType(),
            existing.firstName(),
            existing.lastName(),
            existing.displayName(),
            existing.companyName(),
            existing.email(),
            existing.phone(),
            existing.status(),
            existing.sourceDetail(),
            existing.estimatedValue(),
            existing.priority(),
            existing.intentScore(),
            existing.referrerId(),
            newOwnerUserId,
            existing.createdByUserId(),
            existing.notes(),
            existing.createdAt(),
            Instant.now()
        );
        leadRepository.update(updated);

        // 3. Log timeline activity
        activityService.logActivity(
            leadId,
            ManageMyMarketConstants.ACTIVITY_TYPE_ASSIGNMENT_CHANGE,
            "Lead Reassigned",
            transferReason != null ? "Reason: " + transferReason : "Owner changed",
            oldOwner,
            newOwnerUserId,
            null,
            null,
            assignedByUserId
        );
    }

    public List<LeadAssignment> listAssignments(String leadId) {
        return assignmentRepository.listByLead(leadId);
    }
}
