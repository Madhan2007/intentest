SELECT cc.channel_type,
       COUNT(DISTINCT cc.campaign_id) AS campaign_count,
       COALESCE(SUM(cc.budget), 0) AS total_budget,
       COALESCE(SUM(cc.spent), 0) AS total_spent
FROM mkt_campaign_channel cc
JOIN mkt_campaign c ON cc.campaign_id = c.id
WHERE c.company_id = :company_id::uuid
  AND (:from_date::timestamptz IS NULL OR c.created_at >= :from_date::timestamptz)
  AND (:to_date::timestamptz IS NULL OR c.created_at <= :to_date::timestamptz)
GROUP BY cc.channel_type
ORDER BY total_spent DESC;
