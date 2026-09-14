SELECT f.assigned_to_user_id,
       COUNT(*) AS total_followups,
       COUNT(*) FILTER (WHERE f.status = 'COMPLETED') AS completed_count,
       COUNT(*) FILTER (WHERE f.status = 'PENDING' AND f.due_at < NOW()) AS overdue_count,
       COUNT(*) FILTER (WHERE f.status = 'PENDING' AND f.due_at >= NOW()) AS upcoming_count,
       ROUND(COUNT(*) FILTER (WHERE f.status = 'COMPLETED')::numeric / NULLIF(COUNT(*), 0) * 100, 2) AS compliance_rate_pct
FROM mkt_lead_followup f
WHERE f.company_id = :company_id::uuid
  AND (:from_date::timestamptz IS NULL OR f.due_at >= :from_date::timestamptz)
  AND (:to_date::timestamptz IS NULL OR f.due_at <= :to_date::timestamptz)
GROUP BY f.assigned_to_user_id
ORDER BY total_followups DESC;
