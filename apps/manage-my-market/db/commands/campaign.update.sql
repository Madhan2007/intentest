UPDATE mkt_campaign
SET campaign_name = :campaign_name,
    status = COALESCE(:status, status),
    start_date = :start_date::date,
    end_date = :end_date::date,
    budget = :budget,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
