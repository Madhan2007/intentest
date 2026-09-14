INSERT INTO mkt_lead_followup (
    id, lead_id, reason, scheduled_at, assigned_agent_user_id,
    priority, status, notes, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :lead_id::uuid,
    :reason,
    :scheduled_at::timestamptz,
    :assigned_agent_user_id::uuid,
    :priority,
    COALESCE(:status, 'SCHEDULED'),
    :notes,
    now(),
    now()
);
