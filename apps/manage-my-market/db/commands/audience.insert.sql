INSERT INTO mkt_audience (
    id, company_id, audience_name, description, rules_json, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :audience_name,
    :description,
    :rules_json::jsonb,
    now(),
    now()
);
