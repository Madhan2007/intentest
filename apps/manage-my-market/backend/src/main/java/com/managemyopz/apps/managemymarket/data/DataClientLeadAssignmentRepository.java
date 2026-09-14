/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadAssignment;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Lead assignment operations.
 */
public class DataClientLeadAssignmentRepository implements LeadAssignmentRepository {

    private final DataClient dataClient;

    public DataClientLeadAssignmentRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(LeadAssignment assignment) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", assignment.id());
        params.put("lead_id", assignment.leadId());
        params.put("assigned_by_user_id", assignment.assignedByUserId());
        params.put("assigned_to_user_id", assignment.assignedToUserId());
        params.put("transfer_reason", assignment.transferReason());

        dataClient.execute(MarketDataConstants.LEAD_ASSIGNMENT_INSERT, params);
    }

    @Override
    public List<LeadAssignment> listByLead(String leadId) {
        return dataClient.query(MarketDataConstants.LEAD_ASSIGNMENT_LIST_BY_LEAD, Map.of("lead_id", leadId)).stream()
            .map(DataClientLeadAssignmentRepository::assignmentFromRow)
            .toList();
    }

    private static LeadAssignment assignmentFromRow(Row row) {
        return new LeadAssignment(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.get("assigned_by_user_id") != null ? row.get("assigned_by_user_id").toString() : null,
            row.get("assigned_to_user_id") != null ? row.get("assigned_to_user_id").toString() : null,
            row.getString("transfer_reason"),
            row.getInstant("assigned_at")
        );
    }
}
