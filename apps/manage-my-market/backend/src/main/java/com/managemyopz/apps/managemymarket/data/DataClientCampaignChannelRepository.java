/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CampaignChannel;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataClient-backed repository for Campaign channel operations.
 */
public class DataClientCampaignChannelRepository implements CampaignChannelRepository {

    private final DataClient dataClient;

    public DataClientCampaignChannelRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(CampaignChannel channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", channel.id());
        params.put("campaign_id", channel.campaignId());
        params.put("channel_type", channel.channelType());
        params.put("budget", channel.budget());
        params.put("spend", channel.spend());

        dataClient.execute(MarketDataConstants.CAMPAIGN_CHANNEL_INSERT, params);
    }

    @Override
    public List<CampaignChannel> listByCampaign(String campaignId) {
        return dataClient.query(MarketDataConstants.CAMPAIGN_CHANNEL_LIST_BY_CAMPAIGN, Map.of("campaign_id", campaignId)).stream()
            .map(DataClientCampaignChannelRepository::channelFromRow)
            .toList();
    }

    private static CampaignChannel channelFromRow(Row row) {
        return new CampaignChannel(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("campaign_id") != null ? row.get("campaign_id").toString() : null,
            row.getString("channel_type"),
            row.getDecimal("budget"),
            row.getDecimal("spend"),
            row.getInstant("created_at")
        );
    }
}
