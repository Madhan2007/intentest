SELECT id, campaign_id, channel_type, budget, spend, created_at
FROM mkt_campaign_channel
WHERE campaign_id = :campaign_id::uuid
ORDER BY created_at ASC;
