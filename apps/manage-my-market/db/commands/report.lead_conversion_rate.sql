SELECT lead_source_type,
       COUNT(*) AS total_leads,
       COUNT(*) FILTER (WHERE status = 'WON') AS converted_leads,
       ROUND(COUNT(*) FILTER (WHERE status = 'WON')::numeric / NULLIF(COUNT(*), 0) * 100, 2) AS conversion_rate_pct,
       COALESCE(SUM(estimated_value) FILTER (WHERE status = 'WON'), 0) AS won_value
FROM mkt_lead
WHERE company_id = :company_id::uuid
  AND (:from_date::timestamptz IS NULL OR created_at >= :from_date::timestamptz)
  AND (:to_date::timestamptz IS NULL OR created_at <= :to_date::timestamptz)
GROUP BY lead_source_type
ORDER BY total_leads DESC;
