INSERT INTO mkt_campaign_recipient (
    id, campaign_id, lead_id, status, sent_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :campaign_id::uuid,
    :lead_id::uuid,
    COALESCE(:status, 'PENDING'),
    :sent_at::timestamptz,
    now()
)
ON CONFLICT (campaign_id, lead_id) DO NOTHING;
