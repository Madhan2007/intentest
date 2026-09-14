/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadScore;
import com.managemyopz.kernel.data.client.DataClient;

import java.util.HashMap;
import java.util.Map;

/**
 * DataClient-backed repository for Lead score operations.
 */
public class DataClientLeadScoreRepository implements LeadScoreRepository {

    private final DataClient dataClient;

    public DataClientLeadScoreRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void upsert(LeadScore leadScore) {
        Map<String, Object> params = new HashMap<>();
        params.put("lead_id", leadScore.leadId());
        params.put("score", leadScore.score());
        params.put("breakdown_json", leadScore.breakdownJson());

        dataClient.execute(MarketDataConstants.LEAD_SCORE_UPSERT, params);
    }
}
