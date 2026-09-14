/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadRepository;
import com.managemyopz.apps.managemymarket.domain.Lead;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core application service for Lead management.
 */
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadCodeSequenceService codeSequenceService;
    private final LeadActivityService activityService;

    public LeadService(LeadRepository leadRepository,
                       LeadCodeSequenceService codeSequenceService,
                       LeadActivityService activityService) {
        this.leadRepository = leadRepository;
        this.codeSequenceService = codeSequenceService;
        this.activityService = activityService;
    }

    public Lead createLead(String companyId, String displayName, String leadSourceType,
                           String firstName, String lastName, String companyName, String email,
                           String phone, String sourceDetail, Integer estimatedValue,
                           String priority, String referrerId, String ownerUserId,
                           String createdByUserId, String notes) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display_name is required");
        }
        if (leadSourceType == null || leadSourceType.isBlank()) {
            leadSourceType = ManageMyMarketConstants.LEAD_SOURCE_MANUAL;
        }

        if (ManageMyMarketConstants.LEAD_SOURCE_REFERRAL.equalsIgnoreCase(leadSourceType) && (referrerId == null || referrerId.isBlank())) {
            throw new IllegalArgumentException("referrer_id is required for REFERRAL source");
        }

        String leadCode = codeSequenceService.reserveNextCode(companyId, "LED");
        String id = UUID.randomUUID().toString();

        Lead lead = new Lead(
            id,
            companyId,
            leadCode,
            leadSourceType.toUpperCase(),
            firstName,
            lastName,
            displayName.trim(),
            companyName,
            email,
            phone,
            ManageMyMarketConstants.LEAD_STATUS_NEW,
            sourceDetail,
            estimatedValue,
            priority,
            0,
            referrerId,
            ownerUserId,
            createdByUserId,
            notes,
            Instant.now(),
            Instant.now()
        );

        leadRepository.insert(lead);

        // Record initial activity
        activityService.logActivity(
            id,
            ManageMyMarketConstants.ACTIVITY_TYPE_NOTE,
            "Lead Created",
            "Source: " + leadSourceType + (sourceDetail != null ? " (" + sourceDetail + ")" : ""),
            null,
            null,
            null,
            null,
            createdByUserId
        );

        return lead;
    }

    public Optional<Lead> findById(String id, String companyId) {
        return leadRepository.findById(id, companyId);
    }

    public Optional<Lead> findByCode(String companyId, String leadCode) {
        return leadRepository.findByCompanyAndCode(companyId, leadCode);
    }

    public List<Lead> listPaged(String companyId, String status, String leadSourceType, String ownerUserId, int limit, int offset) {
        int safeLimit = Math.clamp(limit, 1, 200);
        int safeOffset = Math.max(0, offset);
        return leadRepository.listPaged(companyId, status, leadSourceType, ownerUserId, safeLimit, safeOffset);
    }

    public void updateLead(String id, String companyId, String displayName, String firstName,
                           String lastName, String companyName, String email, String phone,
                           String status, String sourceDetail, Integer estimatedValue,
                           String priority, String referrerId, String notes, String updatedByUserId) {
        Optional<Lead> existingOpt = leadRepository.findById(id, companyId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Lead not found: " + id);
        }
        Lead existing = existingOpt.get();

        String oldStatus = existing.status();
        String newStatus = (status != null && !status.isBlank()) ? status.toUpperCase() : oldStatus;

        Lead updated = new Lead(
            id,
            companyId,
            existing.leadCode(),
            existing.leadSourceType(),
            firstName != null ? firstName : existing.firstName(),
            lastName != null ? lastName : existing.lastName(),
            displayName != null ? displayName : existing.displayName(),
            companyName != null ? companyName : existing.companyName(),
            email != null ? email : existing.email(),
            phone != null ? phone : existing.phone(),
            newStatus,
            sourceDetail != null ? sourceDetail : existing.sourceDetail(),
            estimatedValue != null ? estimatedValue : existing.estimatedValue(),
            priority != null ? priority : existing.priority(),
            existing.intentScore(),
            referrerId != null ? referrerId : existing.referrerId(),
            existing.ownerUserId(), // owner is never modified here; only via LeadAssignmentService
            existing.createdByUserId(),
            notes != null ? notes : existing.notes(),
            existing.createdAt(),
            Instant.now()
        );

        leadRepository.update(updated);

        if (!oldStatus.equalsIgnoreCase(newStatus)) {
            activityService.logActivity(
                id,
                ManageMyMarketConstants.ACTIVITY_TYPE_STATUS_CHANGE,
                "Status Changed",
                "Status updated to " + newStatus,
                oldStatus,
                newStatus,
                null,
                null,
                updatedByUserId
            );
        }
    }

    public void disqualify(String id, String companyId, String reason, String performedByUserId) {
        Optional<Lead> existingOpt = leadRepository.findById(id, companyId);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("Lead not found: " + id);
        }
        leadRepository.disqualify(id, companyId, reason);

        activityService.logActivity(
            id,
            ManageMyMarketConstants.ACTIVITY_TYPE_STATUS_CHANGE,
            "Lead Disqualified",
            reason != null ? "Reason: " + reason : "Disqualified",
            existingOpt.get().status(),
            ManageMyMarketConstants.LEAD_STATUS_DISQUALIFIED,
            null,
            null,
            performedByUserId
        );
    }
}
