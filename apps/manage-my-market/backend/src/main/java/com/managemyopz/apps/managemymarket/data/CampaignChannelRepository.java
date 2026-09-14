/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CampaignChannel;

import java.util.List;

/**
 * Data access interface for Campaign channel budget/spend.
 */
public interface CampaignChannelRepository {

    void insert(CampaignChannel channel);

    List<CampaignChannel> listByCampaign(String campaignId);
}
