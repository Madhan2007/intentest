SELECT r.id, r.campaign_id, r.lead_id, r.status, r.sent_at, r.updated_at,
       l.display_name AS lead_name, l.email AS lead_email, l.phone AS lead_phone
FROM mkt_campaign_recipient r
JOIN mkt_lead l ON l.id = r.lead_id
WHERE r.campaign_id = :campaign_id::uuid
  AND (:status IS NULL OR r.status = :status)
ORDER BY r.updated_at DESC
LIMIT :limit OFFSET :offset;
