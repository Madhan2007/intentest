SELECT status, lead_source_type, COUNT(*) AS count, COALESCE(SUM(estimated_value), 0) AS total_estimated_value
FROM mkt_lead
WHERE company_id = :company_id::uuid
  AND (:from_date::timestamptz IS NULL OR created_at >= :from_date::timestamptz)
  AND (:to_date::timestamptz IS NULL OR created_at <= :to_date::timestamptz)
GROUP BY status, lead_source_type
ORDER BY count DESC;
