UPDATE mkt_audience
SET audience_name = :audience_name,
    description = :description,
    rules_json = :rules_json::jsonb,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
