INSERT INTO mkt_call_queue_item (
    id, queue_id, lead_id, priority, status, assigned_agent_user_id,
    call_attempts, last_dialed_at, next_call_scheduled_at, notes, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :queue_id::uuid,
    :lead_id::uuid,
    :priority,
    COALESCE(:status, 'PENDING'),
    :assigned_agent_user_id::uuid,
    0,
    NULL,
    :next_call_scheduled_at::timestamptz,
    :notes,
    now(),
    now()
);
