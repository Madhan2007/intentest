SELECT id, company_id, campaign_name, status, start_date, end_date, budget,
       created_by_user_id, created_at, updated_at
FROM mkt_campaign
WHERE company_id = :company_id::uuid
  AND (:status::text IS NULL OR status = :status::text)
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset;
