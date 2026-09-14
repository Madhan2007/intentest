SELECT id, company_id, audience_name, description, rules_json, created_at, updated_at
FROM mkt_audience
WHERE company_id = :company_id::uuid
ORDER BY audience_name ASC;
