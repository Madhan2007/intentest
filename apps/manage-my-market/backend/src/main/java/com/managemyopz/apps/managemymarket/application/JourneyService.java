/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.JourneyEnrollmentRepository;
import com.managemyopz.apps.managemymarket.data.JourneyRepository;
import com.managemyopz.apps.managemymarket.domain.Journey;
import com.managemyopz.apps.managemymarket.domain.JourneyEnrollment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for multi-step automated marketing journeys.
 */
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyEnrollmentRepository enrollmentRepository;

    public JourneyService(JourneyRepository journeyRepository, JourneyEnrollmentRepository enrollmentRepository) {
        this.journeyRepository = journeyRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public Journey createJourney(String companyId, String journeyName, String status, String triggerType, String flowDefinitionJson) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }
        if (journeyName == null || journeyName.isBlank()) {
            throw new IllegalArgumentException("journey_name is required");
        }

        String id = UUID.randomUUID().toString();
        Journey journey = new Journey(
            id,
            companyId,
            journeyName.trim(),
            status != null ? status.toUpperCase() : ManageMyMarketConstants.JOURNEY_STATUS_DRAFT,
            triggerType != null ? triggerType.toUpperCase() : ManageMyMarketConstants.JOURNEY_TRIGGER_LEAD_CREATED,
            flowDefinitionJson,
            Instant.now(),
            Instant.now()
        );
        journeyRepository.insert(journey);
        return journey;
    }

    public void updateJourney(String id, String companyId, String journeyName, String status, String triggerType, String flowDefinitionJson) {
        Journey journey = new Journey(
            id,
            companyId,
            journeyName,
            status,
            triggerType,
            flowDefinitionJson,
            null,
            Instant.now()
        );
        journeyRepository.update(journey);
    }

    public List<Journey> listByCompany(String companyId) {
        return journeyRepository.listByCompany(companyId);
    }

    public Optional<Journey> getById(String id, String companyId) {
        return journeyRepository.findById(id, companyId);
    }

    public JourneyEnrollment enrollLead(String journeyId, String leadId, String initialNodeId) {
        String id = UUID.randomUUID().toString();
        JourneyEnrollment enrollment = new JourneyEnrollment(
            id,
            journeyId,
            leadId,
            initialNodeId,
            ManageMyMarketConstants.ENROLLMENT_STATUS_ACTIVE,
            Instant.now(),
            Instant.now(),
            null,
            null
        );
        enrollmentRepository.insert(enrollment);
        return enrollment;
    }

    public void advanceNode(String enrollmentId, String nextNodeId, String status) {
        enrollmentRepository.updateNode(enrollmentId, nextNodeId, status);
    }

    public List<JourneyEnrollment> listEnrollments(String journeyId, String status) {
        return enrollmentRepository.listByJourney(journeyId, status);
    }
}
