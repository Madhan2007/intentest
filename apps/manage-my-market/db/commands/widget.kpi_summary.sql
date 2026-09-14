SELECT
  (SELECT COUNT(*) FROM mkt_lead WHERE company_id = :company_id::uuid) AS total_leads,
  (SELECT COUNT(*) FROM mkt_lead WHERE company_id = :company_id::uuid AND created_at >= CURRENT_DATE) AS new_leads_today,
  (SELECT COALESCE(SUM(estimated_value), 0) FROM mkt_lead WHERE company_id = :company_id::uuid) AS total_estimated_value,
  (SELECT COUNT(*) FROM mkt_campaign WHERE company_id = :company_id::uuid AND status = 'ACTIVE') AS active_campaigns,
  (SELECT COALESCE(SUM(budget), 0) FROM mkt_campaign WHERE company_id = :company_id::uuid AND status = 'ACTIVE') AS active_campaigns_budget,
  (SELECT COUNT(*) FROM mkt_call_log WHERE company_id = :company_id::uuid AND called_at >= CURRENT_DATE) AS calls_today,
  (SELECT COUNT(*) FROM mkt_call_queue_item WHERE company_id = :company_id::uuid AND status = 'PENDING') AS pending_queue_items;
