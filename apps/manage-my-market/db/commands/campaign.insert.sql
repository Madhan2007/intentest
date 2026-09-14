INSERT INTO mkt_campaign (
    id, company_id, campaign_name, status, start_date, end_date, budget,
    created_by_user_id, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :campaign_name,
    COALESCE(:status, 'DRAFT'),
    :start_date::date,
    :end_date::date,
    :budget,
    :created_by_user_id::uuid,
    now(),
    now()
);
