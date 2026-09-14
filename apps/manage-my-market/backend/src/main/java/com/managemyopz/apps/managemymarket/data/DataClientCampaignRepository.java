/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.Campaign;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Campaign operations.
 */
public class DataClientCampaignRepository implements CampaignRepository {

    private final DataClient dataClient;

    public DataClientCampaignRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(Campaign campaign) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", campaign.id());
        params.put("company_id", campaign.companyId());
        params.put("campaign_name", campaign.campaignName());
        params.put("status", campaign.status());
        params.put("start_date", campaign.startDate() != null ? Date.valueOf(campaign.startDate()) : null);
        params.put("end_date", campaign.endDate() != null ? Date.valueOf(campaign.endDate()) : null);
        params.put("budget", campaign.budget());
        params.put("created_by_user_id", campaign.createdByUserId());

        dataClient.execute(MarketDataConstants.CAMPAIGN_INSERT, params);
    }

    @Override
    public void update(Campaign campaign) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", campaign.id());
        params.put("company_id", campaign.companyId());
        params.put("campaign_name", campaign.campaignName());
        params.put("status", campaign.status());
        params.put("start_date", campaign.startDate() != null ? Date.valueOf(campaign.startDate()) : null);
        params.put("end_date", campaign.endDate() != null ? Date.valueOf(campaign.endDate()) : null);
        params.put("budget", campaign.budget());

        dataClient.execute(MarketDataConstants.CAMPAIGN_UPDATE, params);
    }

    @Override
    public List<Campaign> listPaged(String companyId, String status, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("company_id", companyId);
        params.put("status", status);
        params.put("limit", limit);
        params.put("offset", offset);

        return dataClient.query(MarketDataConstants.CAMPAIGN_LIST_PAGED, params).stream()
            .map(DataClientCampaignRepository::campaignFromRow)
            .toList();
    }

    @Override
    public Optional<Campaign> findById(String id, String companyId) {
        // Query list paged with id filter or reuse listPaged
        List<Campaign> list = listPaged(companyId, null, 1000, 0);
        return list.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    private static Campaign campaignFromRow(Row row) {
        LocalDate startDate = null;
        Object sdo = row.get("start_date");
        if (sdo instanceof Date d) {
            startDate = d.toLocalDate();
        } else if (sdo instanceof LocalDate ld) {
            startDate = ld;
        } else if (sdo != null) {
            startDate = LocalDate.parse(sdo.toString());
        }

        LocalDate endDate = null;
        Object edo = row.get("end_date");
        if (edo instanceof Date d) {
            endDate = d.toLocalDate();
        } else if (edo instanceof LocalDate ld) {
            endDate = ld;
        } else if (edo != null) {
            endDate = LocalDate.parse(edo.toString());
        }

        return new Campaign(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("company_id") != null ? row.get("company_id").toString() : null,
            row.getString("campaign_name"),
            row.getString("status"),
            startDate,
            endDate,
            row.getDecimal("budget"),
            row.get("created_by_user_id") != null ? row.get("created_by_user_id").toString() : null,
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
