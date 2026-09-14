/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.kernel.data.client.DataClient;

import java.util.Map;

/**
 * DataClient-backed atomic counter for lead code sequences.
 */
public class DataClientLeadCodeSequenceRepository implements LeadCodeSequenceRepository {

    private final DataClient dataClient;

    public DataClientLeadCodeSequenceRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public long reserveNext(String companyId, String prefix) {
        Map<String, Object> params = Map.of(
            "company_id", companyId,
            "prefix", prefix == null ? "" : prefix
        );
        return dataClient.queryOne(MarketDataConstants.LEAD_CODE_SEQUENCE_RESERVE_NEXT, params)
            .map(row -> row.getLong("reserved_number"))
            .orElse(1L);
    }
}
