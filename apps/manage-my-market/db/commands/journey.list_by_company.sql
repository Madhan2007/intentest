SELECT id, company_id, journey_name, status, trigger_type, flow_definition_json, created_at, updated_at
FROM mkt_journey
WHERE company_id = :company_id::uuid
ORDER BY created_at DESC;
