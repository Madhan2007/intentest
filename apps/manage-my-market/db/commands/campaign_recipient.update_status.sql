UPDATE mkt_campaign_recipient
SET status = :status,
    sent_at = CASE WHEN :status = 'SENT' AND sent_at IS NULL THEN now() ELSE sent_at END,
    updated_at = now()
WHERE campaign_id = :campaign_id::uuid AND lead_id = :lead_id::uuid;
