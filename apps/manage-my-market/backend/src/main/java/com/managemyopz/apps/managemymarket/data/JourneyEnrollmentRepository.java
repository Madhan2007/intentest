/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.JourneyEnrollment;

import java.util.List;

/**
 * Data access interface for Journey enrollments.
 */
public interface JourneyEnrollmentRepository {

    void insert(JourneyEnrollment enrollment);

    void updateNode(String id, String currentNodeId, String status);

    List<JourneyEnrollment> listByJourney(String journeyId, String status);
}
