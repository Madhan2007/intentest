/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadAssignment;

import java.util.List;

/**
 * Data access interface for Lead assignment history.
 */
public interface LeadAssignmentRepository {

    void insert(LeadAssignment assignment);

    List<LeadAssignment> listByLead(String leadId);
}
