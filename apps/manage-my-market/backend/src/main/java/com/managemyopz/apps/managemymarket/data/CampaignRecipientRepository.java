/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CampaignRecipient;

import java.util.List;

/**
 * Data access interface for Campaign recipients.
 */
public interface CampaignRecipientRepository {

    void insert(CampaignRecipient recipient);

    void updateStatus(String campaignId, String leadId, String status);

    List<CampaignRecipient> listByCampaign(String campaignId, String status, int limit, int offset);
}
