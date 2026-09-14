/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.CampaignChannelRepository;
import com.managemyopz.apps.managemymarket.data.CampaignRecipientRepository;
import com.managemyopz.apps.managemymarket.data.CampaignRepository;
import com.managemyopz.apps.managemymarket.domain.Campaign;
import com.managemyopz.apps.managemymarket.domain.CampaignChannel;
import com.managemyopz.apps.managemymarket.domain.CampaignRecipient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing marketing campaigns, channels, and recipient enrollments.
 */
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignChannelRepository channelRepository;
    private final CampaignRecipientRepository recipientRepository;

    public CampaignService(CampaignRepository campaignRepository,
                           CampaignChannelRepository channelRepository,
                           CampaignRecipientRepository recipientRepository) {
        this.campaignRepository = campaignRepository;
        this.channelRepository = channelRepository;
        this.recipientRepository = recipientRepository;
    }

    public Campaign createCampaign(String companyId, String campaignName, String status,
                                   LocalDate startDate, LocalDate endDate, BigDecimal budget,
                                   String createdByUserId) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }
        if (campaignName == null || campaignName.isBlank()) {
            throw new IllegalArgumentException("campaign_name is required");
        }

        String id = UUID.randomUUID().toString();
        Campaign campaign = new Campaign(
            id,
            companyId,
            campaignName.trim(),
            status != null ? status.toUpperCase() : ManageMyMarketConstants.CAMPAIGN_STATUS_DRAFT,
            startDate,
            endDate,
            budget,
            createdByUserId,
            Instant.now(),
            Instant.now()
        );
        campaignRepository.insert(campaign);
        return campaign;
    }

    public void updateCampaign(String id, String companyId, String campaignName, String status,
                               LocalDate startDate, LocalDate endDate, BigDecimal budget) {
        Campaign campaign = new Campaign(
            id,
            companyId,
            campaignName,
            status,
            startDate,
            endDate,
            budget,
            null,
            null,
            Instant.now()
        );
        campaignRepository.update(campaign);
    }

    public List<Campaign> listPaged(String companyId, String status, int limit, int offset) {
        int safeLimit = Math.clamp(limit, 1, 100);
        int safeOffset = Math.max(0, offset);
        return campaignRepository.listPaged(companyId, status, safeLimit, safeOffset);
    }

    public Optional<Campaign> getById(String id, String companyId) {
        return campaignRepository.findById(id, companyId);
    }

    public CampaignChannel addChannel(String campaignId, String channelType, BigDecimal budget, BigDecimal spend) {
        String id = UUID.randomUUID().toString();
        CampaignChannel channel = new CampaignChannel(
            id,
            campaignId,
            channelType,
            budget != null ? budget : BigDecimal.ZERO,
            spend != null ? spend : BigDecimal.ZERO,
            Instant.now()
        );
        channelRepository.insert(channel);
        return channel;
    }

    public List<CampaignChannel> listChannels(String campaignId) {
        return channelRepository.listByCampaign(campaignId);
    }

    public void enrollRecipients(String campaignId, List<String> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            return;
        }
        for (String leadId : leadIds) {
            CampaignRecipient recipient = new CampaignRecipient(
                UUID.randomUUID().toString(),
                campaignId,
                leadId,
                ManageMyMarketConstants.RECIPIENT_STATUS_PENDING,
                null,
                Instant.now(),
                null,
                null,
                null
            );
            recipientRepository.insert(recipient);
        }
    }

    public void updateRecipientStatus(String campaignId, String leadId, String status) {
        recipientRepository.updateStatus(campaignId, leadId, status);
    }

    public List<CampaignRecipient> listRecipients(String campaignId, String status, int limit, int offset) {
        int safeLimit = Math.clamp(limit, 1, 200);
        int safeOffset = Math.max(0, offset);
        return recipientRepository.listByCampaign(campaignId, status, safeLimit, safeOffset);
    }
}
