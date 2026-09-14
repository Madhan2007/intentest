/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Lead;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Lead operations.
 */
public class DataClientLeadRepository implements LeadRepository {

    private final DataClient dataClient;

    public DataClientLeadRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public Optional<Lead> findById(String id, String companyId) {
        Map<String, Object> params = Map.of(
            "id", id,
            "company_id", companyId
        );
        return dataClient.queryOne(MarketDataConstants.LEAD_FIND_BY_ID, params)
            .map(DataClientLeadRepository::leadFromRow);
    }

    @Override
    public Optional<Lead> findByCompanyAndCode(String companyId, String leadCode) {
        Map<String, Object> params = Map.of(
            "company_id", companyId,
            "lead_code", leadCode
        );
        return dataClient.queryOne(MarketDataConstants.LEAD_FIND_BY_COMPANY_AND_CODE, params)
            .map(DataClientLeadRepository::leadFromRow);
    }

    @Override
    public List<Lead> listPaged(String companyId, String status, String leadSourceType, String ownerUserId, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("status", status);
        params.put("lead_source_type", leadSourceType);
        params.put("owner_user_id", ownerUserId);
        params.put("limit", limit);
        params.put("offset", offset);

        return dataClient.query(MarketDataConstants.LEAD_LIST_PAGED, params).stream()
            .map(DataClientLeadRepository::leadFromRow)
            .toList();
    }

    @Override
    public void insert(Lead lead) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", lead.id());
        params.put("company_id", lead.companyId());
        params.put("lead_code", lead.leadCode());
        params.put("lead_source_type", lead.leadSourceType());
        params.put("first_name", lead.firstName());
        params.put("last_name", lead.lastName());
        params.put("display_name", lead.displayName());
        params.put("company_name", lead.companyName());
        params.put("email", lead.email());
        params.put("phone", lead.phone());
        params.put("status", lead.status());
        params.put("source_detail", lead.sourceDetail());
        params.put("estimated_value", lead.estimatedValue());
        params.put("priority", lead.priority());
        params.put("intent_score", lead.intentScore());
        params.put("referrer_id", lead.referrerId());
        params.put("owner_user_id", lead.ownerUserId());
        params.put("created_by_user_id", lead.createdByUserId());
        params.put("notes", lead.notes());

        dataClient.execute(MarketDataConstants.LEAD_INSERT, params);
    }

    @Override
    public void update(Lead lead) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", lead.id());
        params.put("company_id", lead.companyId());
        params.put("first_name", lead.firstName());
        params.put("last_name", lead.lastName());
        params.put("display_name", lead.displayName());
        params.put("company_name", lead.companyName());
        params.put("email", lead.email());
        params.put("phone", lead.phone());
        params.put("status", lead.status());
        params.put("source_detail", lead.sourceDetail());
        params.put("estimated_value", lead.estimatedValue());
        params.put("priority", lead.priority());
        params.put("intent_score", lead.intentScore());
        params.put("referrer_id", lead.referrerId());
        params.put("notes", lead.notes());

        dataClient.execute(MarketDataConstants.LEAD_UPDATE, params);
    }

    @Override
    public void disqualify(String id, String companyId, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("company_id", companyId);
        params.put("reason", reason);

        dataClient.execute(MarketDataConstants.LEAD_DISQUALIFY, params);
    }

    static Lead leadFromRow(Row row) {
        Object estVal = row.get("estimated_value");
        Integer estimatedValue = estVal instanceof Number n ? n.intValue() : null;

        Object intSc = row.get("intent_score");
        Integer intentScore = intSc instanceof Number n ? n.intValue() : null;

        return new Lead(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("lead_code"),
            row.getString("lead_source_type"),
            row.getString("first_name"),
            row.getString("last_name"),
            row.getString("display_name"),
            row.getString("company_name"),
            row.getString("email"),
            row.getString("phone"),
            row.getString("status"),
            row.getString("source_detail"),
            estimatedValue,
            row.getString("priority"),
            intentScore,
            row.get("referrer_id") != null ? row.get("referrer_id").toString() : null,
            row.get("owner_user_id") != null ? row.get("owner_user_id").toString() : null,
            row.get("created_by_user_id") != null ? row.get("created_by_user_id").toString() : null,
            row.getString("notes"),
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
