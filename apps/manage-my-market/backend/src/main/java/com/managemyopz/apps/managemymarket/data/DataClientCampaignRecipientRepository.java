/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CampaignRecipient;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Campaign recipient operations.
 */
public class DataClientCampaignRecipientRepository implements CampaignRecipientRepository {

    private final DataClient dataClient;

    public DataClientCampaignRecipientRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(CampaignRecipient recipient) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", recipient.id());
        params.put("campaign_id", recipient.campaignId());
        params.put("lead_id", recipient.leadId());
        params.put("status", recipient.status());
        params.put("sent_at", recipient.sentAt());

        dataClient.execute(MarketDataConstants.CAMPAIGN_RECIPIENT_INSERT, params);
    }

    @Override
    public void updateStatus(String campaignId, String leadId, String status) {
        Map<String, Object> params = Map.of(
            "campaign_id", campaignId,
            "lead_id", leadId,
            "status", status
        );
        dataClient.execute(MarketDataConstants.CAMPAIGN_RECIPIENT_UPDATE_STATUS, params);
    }

    @Override
    public List<CampaignRecipient> listByCampaign(String campaignId, String status, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("campaign_id", campaignId);
        params.put("status", status);
        params.put("limit", limit);
        params.put("offset", offset);

        return dataClient.query(MarketDataConstants.CAMPAIGN_RECIPIENT_LIST_BY_CAMPAIGN, params).stream()
            .map(DataClientCampaignRecipientRepository::recipientFromRow)
            .toList();
    }

    private static CampaignRecipient recipientFromRow(Row row) {
        return new CampaignRecipient(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("campaign_id") != null ? row.get("campaign_id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getString("status"),
            row.getInstant("sent_at"),
            row.getInstant("updated_at"),
            row.getString("lead_name"),
            row.getString("lead_email"),
            row.getString("lead_phone")
        );
    }
}
