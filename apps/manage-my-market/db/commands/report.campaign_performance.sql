SELECT c.id,
       c.code,
       c.name,
       c.status,
       c.budget,
       c.spent,
       COUNT(DISTINCT cr.id) AS total_recipients,
       COUNT(DISTINCT cr.id) FILTER (WHERE cr.status = 'DELIVERED') AS delivered_count,
       COUNT(DISTINCT cr.id) FILTER (WHERE cr.status = 'OPENED') AS opened_count,
       COUNT(DISTINCT cr.id) FILTER (WHERE cr.status = 'CLICKED') AS clicked_count,
       COUNT(DISTINCT cr.id) FILTER (WHERE cr.status = 'CONVERTED') AS converted_count
FROM mkt_campaign c
LEFT JOIN mkt_campaign_recipient cr ON c.id = cr.campaign_id
WHERE c.company_id = :company_id::uuid
  AND (:status::varchar IS NULL OR c.status = :status)
GROUP BY c.id, c.code, c.name, c.status, c.budget, c.spent
ORDER BY c.created_at DESC;
