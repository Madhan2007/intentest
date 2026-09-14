UPDATE mkt_journey
SET journey_name = :journey_name,
    status = COALESCE(:status, status),
    trigger_type = :trigger_type,
    flow_definition_json = :flow_definition_json::jsonb,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
