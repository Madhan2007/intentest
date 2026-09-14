/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

/**
 * Domain constants for Manage My Market.
 */
public final class ManageMyMarketConstants {

    public static final String MODULE_ID = "manage-my-market";
    public static final String DATABASE_NAME = "OPZMARKET";
    public static final String API_PREFIX = "/api/v1/opzhub/manage-my-market";

    // Feature IDs
    public static final String FEATURE_LEAD = "led";
    public static final String FEATURE_CAMPAIGN = "cmp";
    public static final String FEATURE_JOURNEY = "jny";
    public static final String FEATURE_CALL_QUEUE = "cal";
    public static final String FEATURE_REFERRAL = "ref";
    public static final String FEATURE_AGENT = "agt";

    // Lead Sources
    public static final String LEAD_SOURCE_MANUAL = "MANUAL";
    public static final String LEAD_SOURCE_INBOX = "INBOX";
    public static final String LEAD_SOURCE_REFERRAL = "REFERRAL";
    public static final String LEAD_SOURCE_CAMPAIGN = "CAMPAIGN";
    public static final String LEAD_SOURCE_SOCIAL_MENTION = "SOCIAL_MENTION";

    // Lead Statuses
    public static final String LEAD_STATUS_NEW = "NEW";
    public static final String LEAD_STATUS_CONTACTED = "CONTACTED";
    public static final String LEAD_STATUS_QUALIFIED = "QUALIFIED";
    public static final String LEAD_STATUS_CONVERTED = "CONVERTED";
    public static final String LEAD_STATUS_LOST = "LOST";
    public static final String LEAD_STATUS_DISQUALIFIED = "DISQUALIFIED";

    // Priority
    public static final String PRIORITY_HOT = "HOT";
    public static final String PRIORITY_WARM = "WARM";
    public static final String PRIORITY_COLD = "COLD";

    public static final int LEAD_CODE_MAX_LEN = 32;

    // Campaign Statuses
    public static final String CAMPAIGN_STATUS_DRAFT = "DRAFT";
    public static final String CAMPAIGN_STATUS_SCHEDULED = "SCHEDULED";
    public static final String CAMPAIGN_STATUS_ACTIVE = "ACTIVE";
    public static final String CAMPAIGN_STATUS_PAUSED = "PAUSED";
    public static final String CAMPAIGN_STATUS_COMPLETED = "COMPLETED";
    public static final String CAMPAIGN_STATUS_CANCELLED = "CANCELLED";

    // Campaign Channel Types
    public static final String CHANNEL_TYPE_EMAIL = "EMAIL";
    public static final String CHANNEL_TYPE_SMS = "SMS";
    public static final String CHANNEL_TYPE_SOCIAL = "SOCIAL";
    public static final String CHANNEL_TYPE_SEARCH_ADS = "SEARCH_ADS";
    public static final String CHANNEL_TYPE_DISPLAY_ADS = "DISPLAY_ADS";
    public static final String CHANNEL_TYPE_CUSTOM = "CUSTOM";

    // Campaign Recipient Statuses
    public static final String RECIPIENT_STATUS_PENDING = "PENDING";
    public static final String RECIPIENT_STATUS_SENT = "SENT";
    public static final String RECIPIENT_STATUS_DELIVERED = "DELIVERED";
    public static final String RECIPIENT_STATUS_OPENED = "OPENED";
    public static final String RECIPIENT_STATUS_CLICKED = "CLICKED";
    public static final String RECIPIENT_STATUS_BOUNCED = "BOUNCED";
    public static final String RECIPIENT_STATUS_UNSUBSCRIBED = "UNSUBSCRIBED";

    // Journey Statuses
    public static final String JOURNEY_STATUS_DRAFT = "DRAFT";
    public static final String JOURNEY_STATUS_ACTIVE = "ACTIVE";
    public static final String JOURNEY_STATUS_PAUSED = "PAUSED";
    public static final String JOURNEY_STATUS_ARCHIVED = "ARCHIVED";

    // Journey Trigger Types
    public static final String JOURNEY_TRIGGER_LEAD_CREATED = "LEAD_CREATED";
    public static final String JOURNEY_TRIGGER_TAG_ADDED = "TAG_ADDED";
    public static final String JOURNEY_TRIGGER_CAMPAIGN_RESPONSE = "CAMPAIGN_RESPONSE";
    public static final String JOURNEY_TRIGGER_MANUAL = "MANUAL";
    public static final String JOURNEY_TRIGGER_CUSTOM = "CUSTOM";

    // Journey Enrollment Statuses
    public static final String ENROLLMENT_STATUS_ACTIVE = "ACTIVE";
    public static final String ENROLLMENT_STATUS_COMPLETED = "COMPLETED";
    public static final String ENROLLMENT_STATUS_EXITED = "EXITED";

    // Call Queue Statuses
    public static final String CALL_QUEUE_STATUS_ACTIVE = "ACTIVE";
    public static final String CALL_QUEUE_STATUS_PAUSED = "PAUSED";
    public static final String CALL_QUEUE_STATUS_ARCHIVED = "ARCHIVED";

    // Call Queue Item Statuses
    public static final String CALL_ITEM_STATUS_PENDING = "PENDING";
    public static final String CALL_ITEM_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String CALL_ITEM_STATUS_COMPLETED = "COMPLETED";
    public static final String CALL_ITEM_STATUS_SKIPPED = "SKIPPED";

    // Call Log Connection Statuses
    public static final String CALL_CONNECTION_CONNECTED = "CONNECTED";
    public static final String CALL_CONNECTION_NO_ANSWER = "NO_ANSWER";
    public static final String CALL_CONNECTION_BUSY = "BUSY";
    public static final String CALL_CONNECTION_FAILED = "FAILED";
    public static final String CALL_CONNECTION_VOICEMAIL = "VOICEMAIL";

    // Follow-up Statuses
    public static final String FOLLOWUP_STATUS_SCHEDULED = "SCHEDULED";
    public static final String FOLLOWUP_STATUS_OVERDUE = "OVERDUE";
    public static final String FOLLOWUP_STATUS_RESCHEDULED = "RESCHEDULED";
    public static final String FOLLOWUP_STATUS_COMPLETED = "COMPLETED";
    public static final String FOLLOWUP_STATUS_MISSED = "MISSED";
    public static final String FOLLOWUP_STATUS_CANCELLED = "CANCELLED";

    // Referrer Statuses
    public static final String REFERRER_STATUS_ACTIVE = "ACTIVE";
    public static final String REFERRER_STATUS_INACTIVE = "INACTIVE";

    // Referral Reward Statuses
    public static final String REWARD_STATUS_PENDING = "PENDING";
    public static final String REWARD_STATUS_APPROVED = "APPROVED";
    public static final String REWARD_STATUS_PAID = "PAID";
    public static final String REWARD_STATUS_REJECTED = "REJECTED";

    // Marketing Agent Types
    public static final String AGENT_TYPE_TELECALLER = "TELECALLER";
    public static final String AGENT_TYPE_DIGITAL_MARKETER = "DIGITAL_MARKETER";
    public static final String AGENT_TYPE_FIELD_AGENT = "FIELD_AGENT";
    public static final String AGENT_TYPE_CAMPAIGN_MANAGER = "CAMPAIGN_MANAGER";
    public static final String AGENT_TYPE_CUSTOM = "CUSTOM";

    // Activity Types
    public static final String ACTIVITY_TYPE_NOTE = "NOTE";
    public static final String ACTIVITY_TYPE_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTIVITY_TYPE_ASSIGNMENT_CHANGE = "ASSIGNMENT_CHANGE";
    public static final String ACTIVITY_TYPE_CALL = "CALL";
    public static final String ACTIVITY_TYPE_FOLLOWUP = "FOLLOWUP";
    public static final String ACTIVITY_TYPE_EMAIL = "EMAIL";

    private ManageMyMarketConstants() {
    }
}
