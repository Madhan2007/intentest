INSERT INTO mkt_journey (
    id, company_id, journey_name, status, trigger_type, flow_definition_json, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :journey_name,
    COALESCE(:status, 'DRAFT'),
    :trigger_type,
    :flow_definition_json::jsonb,
    now(),
    now()
);
