/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Named SQL command constants for Manage My Market data operations.
 */
package com.managemyopz.apps.managemymarket.data;

/**
 * Named SQL command keys resolved by CommandCatalog.
 */
public final class MarketDataConstants {

    // Lead
    public static final String LEAD_INSERT = "lead.insert";
    public static final String LEAD_FIND_BY_ID = "lead.find_by_id";
    public static final String LEAD_FIND_BY_COMPANY_AND_CODE = "lead.find_by_company_and_code";
    public static final String LEAD_LIST_PAGED = "lead.list_paged";
    public static final String LEAD_UPDATE = "lead.update";
    public static final String LEAD_DISQUALIFY = "lead.disqualify";

    // Lead Code Sequence
    public static final String LEAD_CODE_SEQUENCE_RESERVE_NEXT = "lead_code_sequence.reserve_next";

    // Lead Assignment
    public static final String LEAD_ASSIGNMENT_INSERT = "lead_assignment.insert";
    public static final String LEAD_ASSIGNMENT_LIST_BY_LEAD = "lead_assignment.list_by_lead";

    // Lead Activity
    public static final String LEAD_ACTIVITY_INSERT = "lead_activity.insert";
    public static final String LEAD_ACTIVITY_LIST_BY_LEAD_PAGED = "lead_activity.list_by_lead_paged";

    // Lead Score
    public static final String LEAD_SCORE_UPSERT = "lead_score.upsert";

    // Lead Followup
    public static final String LEAD_FOLLOWUP_INSERT = "lead_followup.insert";
    public static final String LEAD_FOLLOWUP_UPDATE = "lead_followup.update";
    public static final String LEAD_FOLLOWUP_LIST_BY_LEAD = "lead_followup.list_by_lead";
    public static final String LEAD_FOLLOWUP_LIST_DUE = "lead_followup.list_due";

    // Campaign
    public static final String CAMPAIGN_INSERT = "campaign.insert";
    public static final String CAMPAIGN_UPDATE = "campaign.update";
    public static final String CAMPAIGN_LIST_PAGED = "campaign.list_paged";

    // Campaign Channel
    public static final String CAMPAIGN_CHANNEL_INSERT = "campaign_channel.insert";
    public static final String CAMPAIGN_CHANNEL_LIST_BY_CAMPAIGN = "campaign_channel.list_by_campaign";

    // Campaign Recipient
    public static final String CAMPAIGN_RECIPIENT_INSERT = "campaign_recipient.insert";
    public static final String CAMPAIGN_RECIPIENT_UPDATE_STATUS = "campaign_recipient.update_status";
    public static final String CAMPAIGN_RECIPIENT_LIST_BY_CAMPAIGN = "campaign_recipient.list_by_campaign";

    // Email Template
    public static final String EMAIL_TEMPLATE_INSERT = "email_template.insert";
    public static final String EMAIL_TEMPLATE_UPDATE = "email_template.update";
    public static final String EMAIL_TEMPLATE_LIST_BY_COMPANY = "email_template.list_by_company";

    // Audience
    public static final String AUDIENCE_INSERT = "audience.insert";
    public static final String AUDIENCE_UPDATE = "audience.update";
    public static final String AUDIENCE_LIST_BY_COMPANY = "audience.list_by_company";

    // Journey
    public static final String JOURNEY_INSERT = "journey.insert";
    public static final String JOURNEY_UPDATE = "journey.update";
    public static final String JOURNEY_LIST_BY_COMPANY = "journey.list_by_company";

    // Journey Enrollment
    public static final String JOURNEY_ENROLLMENT_INSERT = "journey_enrollment.insert";
    public static final String JOURNEY_ENROLLMENT_UPDATE_NODE = "journey_enrollment.update_node";
    public static final String JOURNEY_ENROLLMENT_LIST_BY_JOURNEY = "journey_enrollment.list_by_journey";

    // Call Queue
    public static final String CALL_QUEUE_INSERT = "call_queue.insert";
    public static final String CALL_QUEUE_LIST_BY_COMPANY = "call_queue.list_by_company";

    // Call Queue Item
    public static final String CALL_QUEUE_ITEM_INSERT = "call_queue_item.insert";
    public static final String CALL_QUEUE_ITEM_UPDATE = "call_queue_item.update";
    public static final String CALL_QUEUE_ITEM_LIST_BY_QUEUE = "call_queue_item.list_by_queue";

    // Call Log
    public static final String CALL_LOG_INSERT = "call_log.insert";
    public static final String CALL_LOG_LIST_BY_LEAD = "call_log.list_by_lead";

    // Referrer
    public static final String REFERRER_INSERT = "referrer.insert";
    public static final String REFERRER_UPDATE = "referrer.update";
    public static final String REFERRER_LIST_BY_COMPANY = "referrer.list_by_company";
    public static final String REFERRER_RECOMPUTE_AGGREGATES = "referrer.recompute_aggregates";

    // Referral Reward
    public static final String REFERRAL_REWARD_INSERT = "referral_reward.insert";
    public static final String REFERRAL_REWARD_UPDATE_STATUS = "referral_reward.update_status";
    public static final String REFERRAL_REWARD_LIST_BY_REFERRER = "referral_reward.list_by_referrer";

    // Agent Profile
    public static final String AGENT_PROFILE_UPSERT = "agent_profile.upsert";
    public static final String AGENT_PROFILE_FIND_BY_USER_ID = "agent_profile.find_by_user_id";

    // Report Commands
    public static final String REPORT_LEAD_SUMMARY = "report.lead_summary";
    public static final String REPORT_LEAD_CONVERSION_RATE = "report.lead_conversion_rate";
    public static final String REPORT_CAMPAIGN_PERFORMANCE = "report.campaign_performance";
    public static final String REPORT_CHANNEL_SPEND = "report.channel_spend";
    public static final String REPORT_TELECALLING_STATS = "report.telecalling_stats";
    public static final String REPORT_REFERRER_LEADERBOARD = "report.referrer_leaderboard";
    public static final String REPORT_FOLLOWUP_COMPLIANCE = "report.followup_compliance";
    public static final String REPORT_JOURNEY_ENROLLMENT_FUNNEL = "report.journey_enrollment_funnel";

    private MarketDataConstants() {
    }
}
