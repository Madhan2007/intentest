/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.JourneyEnrollment;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Journey enrollment operations.
 */
public class DataClientJourneyEnrollmentRepository implements JourneyEnrollmentRepository {

    private final DataClient dataClient;

    public DataClientJourneyEnrollmentRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(JourneyEnrollment enrollment) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", enrollment.id());
        params.put("journey_id", enrollment.journeyId());
        params.put("lead_id", enrollment.leadId());
        params.put("current_node_id", enrollment.currentNodeId());
        params.put("status", enrollment.status());

        dataClient.execute(MarketDataConstants.JOURNEY_ENROLLMENT_INSERT, params);
    }

    @Override
    public void updateNode(String id, String currentNodeId, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("current_node_id", currentNodeId);
        params.put("status", status);

        dataClient.execute(MarketDataConstants.JOURNEY_ENROLLMENT_UPDATE_NODE, params);
    }

    @Override
    public List<JourneyEnrollment> listByJourney(String journeyId, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("journey_id", journeyId);
        params.put("status", status);

        return dataClient.query(MarketDataConstants.JOURNEY_ENROLLMENT_LIST_BY_JOURNEY, params).stream()
            .map(DataClientJourneyEnrollmentRepository::enrollmentFromRow)
            .toList();
    }

    private static JourneyEnrollment enrollmentFromRow(Row row) {
        return new JourneyEnrollment(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("journey_id") != null ? row.get("journey_id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getString("current_node_id"),
            row.getString("status"),
            row.getInstant("enrolled_at"),
            row.getInstant("updated_at"),
            row.getString("lead_name"),
            row.getString("lead_email")
        );
    }
}
