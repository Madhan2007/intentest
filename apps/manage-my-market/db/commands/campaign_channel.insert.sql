INSERT INTO mkt_campaign_channel (
    id, campaign_id, channel_type, budget, spend, created_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :campaign_id::uuid,
    :channel_type,
    COALESCE(:budget, 0),
    COALESCE(:spend, 0),
    now()
);
