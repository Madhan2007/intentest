/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Registers the Manage My Market application beans when enabled.
 */
package com.managemyopz.apps.managemymarket;

import com.managemyopz.apps.managemymarket.application.AgentProfileService;
import com.managemyopz.apps.managemymarket.application.CallQueueService;
import com.managemyopz.apps.managemymarket.application.CampaignService;
import com.managemyopz.apps.managemymarket.application.JourneyService;
import com.managemyopz.apps.managemymarket.application.LeadActivityService;
import com.managemyopz.apps.managemymarket.application.LeadAssignmentService;
import com.managemyopz.apps.managemymarket.application.LeadCodeSequenceService;
import com.managemyopz.apps.managemymarket.application.LeadFollowupService;
import com.managemyopz.apps.managemymarket.application.LeadScoreService;
import com.managemyopz.apps.managemymarket.application.LeadService;
import com.managemyopz.apps.managemymarket.application.ManageMyMarketConstants;
import com.managemyopz.apps.managemymarket.application.ReferrerService;
import com.managemyopz.apps.managemymarket.data.AgentProfileRepository;
import com.managemyopz.apps.managemymarket.data.AudienceRepository;
import com.managemyopz.apps.managemymarket.data.CallLogRepository;
import com.managemyopz.apps.managemymarket.data.CallQueueItemRepository;
import com.managemyopz.apps.managemymarket.data.CallQueueRepository;
import com.managemyopz.apps.managemymarket.data.CampaignChannelRepository;
import com.managemyopz.apps.managemymarket.data.CampaignRecipientRepository;
import com.managemyopz.apps.managemymarket.data.CampaignRepository;
import com.managemyopz.apps.managemymarket.data.DataClientAgentProfileRepository;
import com.managemyopz.apps.managemymarket.data.DataClientAudienceRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCallLogRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCallQueueItemRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCallQueueRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCampaignChannelRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCampaignRecipientRepository;
import com.managemyopz.apps.managemymarket.data.DataClientCampaignRepository;
import com.managemyopz.apps.managemymarket.data.DataClientEmailTemplateRepository;
import com.managemyopz.apps.managemymarket.data.DataClientJourneyEnrollmentRepository;
import com.managemyopz.apps.managemymarket.data.DataClientJourneyRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadActivityRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadAssignmentRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadCodeSequenceRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadFollowupRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadRepository;
import com.managemyopz.apps.managemymarket.data.DataClientLeadScoreRepository;
import com.managemyopz.apps.managemymarket.data.DataClientReferralRewardRepository;
import com.managemyopz.apps.managemymarket.data.DataClientReferrerRepository;
import com.managemyopz.apps.managemymarket.data.EmailTemplateRepository;
import com.managemyopz.apps.managemymarket.data.JourneyEnrollmentRepository;
import com.managemyopz.apps.managemymarket.data.JourneyRepository;
import com.managemyopz.apps.managemymarket.data.LeadActivityRepository;
import com.managemyopz.apps.managemymarket.data.LeadAssignmentRepository;
import com.managemyopz.apps.managemymarket.data.LeadCodeSequenceRepository;
import com.managemyopz.apps.managemymarket.data.LeadFollowupRepository;
import com.managemyopz.apps.managemymarket.data.LeadRepository;
import com.managemyopz.apps.managemymarket.data.LeadScoreRepository;
import com.managemyopz.apps.managemymarket.data.ReferralRewardRepository;
import com.managemyopz.apps.managemymarket.data.ReferrerRepository;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.apps.managemymarket.api.AgentProfileController;
import com.managemyopz.apps.managemymarket.api.AudienceController;
import com.managemyopz.apps.managemymarket.api.CallQueueController;
import com.managemyopz.apps.managemymarket.api.CampaignController;
import com.managemyopz.apps.managemymarket.api.EmailTemplateController;
import com.managemyopz.apps.managemymarket.api.JourneyController;
import com.managemyopz.apps.managemymarket.api.LeadController;
import com.managemyopz.apps.managemymarket.api.LeadFollowupController;
import com.managemyopz.apps.managemymarket.api.ReferrerController;
import com.managemyopz.apps.managemymarket.api.MarketReportController;
import com.managemyopz.apps.managemymarket.api.MarketWidgetDataController;
import com.managemyopz.apps.managemymarket.application.MarketReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.data.client.DataClientRegistry;
import com.managemyopz.kernel.module.ConditionalOnModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AutoConfiguration for Manage My Market.
 */
@Configuration
@ConditionalOnModule("manage-my-market")
public class ManageMyMarketAutoConfiguration {

    @Bean
    public DataClient marketDataClient(DataClientRegistry dataClientRegistry) {
        return dataClientRegistry.forDatabase(ManageMyMarketConstants.DATABASE_NAME);
    }

    // Repositories
    @Bean
    public LeadRepository marketLeadRepository(DataClient marketDataClient) {
        return new DataClientLeadRepository(marketDataClient);
    }

    @Bean
    public LeadCodeSequenceRepository marketLeadCodeSequenceRepository(DataClient marketDataClient) {
        return new DataClientLeadCodeSequenceRepository(marketDataClient);
    }

    @Bean
    public LeadAssignmentRepository marketLeadAssignmentRepository(DataClient marketDataClient) {
        return new DataClientLeadAssignmentRepository(marketDataClient);
    }

    @Bean
    public LeadActivityRepository marketLeadActivityRepository(DataClient marketDataClient) {
        return new DataClientLeadActivityRepository(marketDataClient);
    }

    @Bean
    public LeadScoreRepository marketLeadScoreRepository(DataClient marketDataClient) {
        return new DataClientLeadScoreRepository(marketDataClient);
    }

    @Bean
    public LeadFollowupRepository marketLeadFollowupRepository(DataClient marketDataClient) {
        return new DataClientLeadFollowupRepository(marketDataClient);
    }

    @Bean
    public CampaignRepository marketCampaignRepository(DataClient marketDataClient) {
        return new DataClientCampaignRepository(marketDataClient);
    }

    @Bean
    public CampaignChannelRepository marketCampaignChannelRepository(DataClient marketDataClient) {
        return new DataClientCampaignChannelRepository(marketDataClient);
    }

    @Bean
    public CampaignRecipientRepository marketCampaignRecipientRepository(DataClient marketDataClient) {
        return new DataClientCampaignRecipientRepository(marketDataClient);
    }

    @Bean
    public EmailTemplateRepository marketEmailTemplateRepository(DataClient marketDataClient) {
        return new DataClientEmailTemplateRepository(marketDataClient);
    }

    @Bean
    public AudienceRepository marketAudienceRepository(DataClient marketDataClient) {
        return new DataClientAudienceRepository(marketDataClient);
    }

    @Bean
    public JourneyRepository marketJourneyRepository(DataClient marketDataClient) {
        return new DataClientJourneyRepository(marketDataClient);
    }

    @Bean
    public JourneyEnrollmentRepository marketJourneyEnrollmentRepository(DataClient marketDataClient) {
        return new DataClientJourneyEnrollmentRepository(marketDataClient);
    }

    @Bean
    public CallQueueRepository marketCallQueueRepository(DataClient marketDataClient) {
        return new DataClientCallQueueRepository(marketDataClient);
    }

    @Bean
    public CallQueueItemRepository marketCallQueueItemRepository(DataClient marketDataClient) {
        return new DataClientCallQueueItemRepository(marketDataClient);
    }

    @Bean
    public CallLogRepository marketCallLogRepository(DataClient marketDataClient) {
        return new DataClientCallLogRepository(marketDataClient);
    }

    @Bean
    public ReferrerRepository marketReferrerRepository(DataClient marketDataClient) {
        return new DataClientReferrerRepository(marketDataClient);
    }

    @Bean
    public ReferralRewardRepository marketReferralRewardRepository(DataClient marketDataClient) {
        return new DataClientReferralRewardRepository(marketDataClient);
    }

    @Bean
    public AgentProfileRepository marketAgentProfileRepository(DataClient marketDataClient) {
        return new DataClientAgentProfileRepository(marketDataClient);
    }

    // Application Services
    @Bean
    public LeadCodeSequenceService marketLeadCodeSequenceService(LeadCodeSequenceRepository marketLeadCodeSequenceRepository) {
        return new LeadCodeSequenceService(marketLeadCodeSequenceRepository);
    }

    @Bean
    public LeadActivityService marketLeadActivityService(LeadActivityRepository marketLeadActivityRepository) {
        return new LeadActivityService(marketLeadActivityRepository);
    }

    @Bean
    public LeadAssignmentService marketLeadAssignmentService(LeadRepository marketLeadRepository,
                                                             LeadAssignmentRepository marketLeadAssignmentRepository,
                                                             LeadActivityService marketLeadActivityService) {
        return new LeadAssignmentService(marketLeadRepository, marketLeadAssignmentRepository, marketLeadActivityService);
    }

    @Bean
    public LeadScoreService marketLeadScoreService(LeadScoreRepository marketLeadScoreRepository) {
        return new LeadScoreService(marketLeadScoreRepository);
    }

    @Bean
    public LeadFollowupService marketLeadFollowupService(LeadFollowupRepository marketLeadFollowupRepository,
                                                         LeadActivityService marketLeadActivityService) {
        return new LeadFollowupService(marketLeadFollowupRepository, marketLeadActivityService);
    }

    @Bean
    public LeadService marketLeadService(LeadRepository marketLeadRepository,
                                         LeadCodeSequenceService marketLeadCodeSequenceService,
                                         LeadActivityService marketLeadActivityService) {
        return new LeadService(marketLeadRepository, marketLeadCodeSequenceService, marketLeadActivityService);
    }

    @Bean
    public CampaignService marketCampaignService(CampaignRepository marketCampaignRepository,
                                                 CampaignChannelRepository marketCampaignChannelRepository,
                                                 CampaignRecipientRepository marketCampaignRecipientRepository) {
        return new CampaignService(marketCampaignRepository, marketCampaignChannelRepository, marketCampaignRecipientRepository);
    }

    @Bean
    public JourneyService marketJourneyService(JourneyRepository marketJourneyRepository,
                                               JourneyEnrollmentRepository marketJourneyEnrollmentRepository) {
        return new JourneyService(marketJourneyRepository, marketJourneyEnrollmentRepository);
    }

    @Bean
    public CallQueueService marketCallQueueService(CallQueueRepository marketCallQueueRepository,
                                                   CallQueueItemRepository marketCallQueueItemRepository,
                                                   CallLogRepository marketCallLogRepository,
                                                   LeadActivityService marketLeadActivityService) {
        return new CallQueueService(marketCallQueueRepository, marketCallQueueItemRepository, marketCallLogRepository, marketLeadActivityService);
    }

    @Bean
    public ReferrerService marketReferrerService(ReferrerRepository marketReferrerRepository,
                                                 ReferralRewardRepository marketReferralRewardRepository) {
        return new ReferrerService(marketReferrerRepository, marketReferralRewardRepository);
    }

    @Bean
    public AgentProfileService marketAgentProfileService(AgentProfileRepository marketAgentProfileRepository) {
        return new AgentProfileService(marketAgentProfileRepository);
    }

    // REST Controllers
    @Bean
    public LeadController marketLeadController(LeadService marketLeadService,
                                              LeadAssignmentService marketLeadAssignmentService,
                                              LeadActivityService marketLeadActivityService,
                                              LeadCodeSequenceService marketLeadCodeSequenceService) {
        return new LeadController(marketLeadService, marketLeadAssignmentService, marketLeadActivityService, marketLeadCodeSequenceService);
    }

    @Bean
    public LeadFollowupController marketLeadFollowupController(LeadFollowupService marketLeadFollowupService) {
        return new LeadFollowupController(marketLeadFollowupService);
    }

    @Bean
    public CampaignController marketCampaignController(CampaignService marketCampaignService) {
        return new CampaignController(marketCampaignService);
    }

    @Bean
    public EmailTemplateController marketEmailTemplateController(EmailTemplateRepository marketEmailTemplateRepository) {
        return new EmailTemplateController(marketEmailTemplateRepository);
    }

    @Bean
    public AudienceController marketAudienceController(AudienceRepository marketAudienceRepository) {
        return new AudienceController(marketAudienceRepository);
    }

    @Bean
    public JourneyController marketJourneyController(JourneyService marketJourneyService) {
        return new JourneyController(marketJourneyService);
    }

    @Bean
    public CallQueueController marketCallQueueController(CallQueueService marketCallQueueService) {
        return new CallQueueController(marketCallQueueService);
    }

    @Bean
    public ReferrerController marketReferrerController(ReferrerService marketReferrerService) {
        return new ReferrerController(marketReferrerService);
    }

    @Bean
    public AgentProfileController marketAgentProfileController(AgentProfileService marketAgentProfileService) {
        return new AgentProfileController(marketAgentProfileService);
    }

    @Bean
    public MarketReportService marketReportService(DataClient marketDataClient, CacheClient cacheClient, ObjectMapper objectMapper) {
        return new MarketReportService(marketDataClient, cacheClient, objectMapper);
    }

    @Bean
    public MarketReportController marketReportController(MarketReportService marketReportService) {
        return new MarketReportController(marketReportService);
    }

    @Bean
    public MarketWidgetDataController marketWidgetDataController(DataClient marketDataClient, MarketReportService marketReportService) {
        return new MarketWidgetDataController(marketDataClient, marketReportService);
    }
}
