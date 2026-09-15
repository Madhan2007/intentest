SELECT status, lead_source_type, COUNT(*) AS count, COALESCE(SUM(estimated_value), 0) AS total_estimated_value
FROM mkt_lead
WHERE company_id = :company_id::uuid
  AND (cast(:from_date as text) IS NULL OR :from_date = '' OR created_at >= cast(:from_date as timestamptz))
  AND (cast(:to_date as text) IS NULL OR :to_date = '' OR created_at <= cast(:to_date as timestamptz))
GROUP BY status, lead_source_type
ORDER BY count DESC;
