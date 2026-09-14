INSERT INTO mkt_call_queue (
    id, company_id, queue_name, description, priority, status, created_by_user_id, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :queue_name,
    :description,
    COALESCE(:priority, 0),
    COALESCE(:status, 'ACTIVE'),
    :created_by_user_id::uuid,
    now(),
    now()
);
