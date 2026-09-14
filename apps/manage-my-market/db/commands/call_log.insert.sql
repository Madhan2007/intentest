INSERT INTO mkt_call_log (
    id, company_id, queue_id, lead_id, agent_user_id, phone_number,
    connection_status, disposition, duration_seconds, recording_url, notes,
    scheduled_followup_at, created_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :queue_id::uuid,
    :lead_id::uuid,
    :agent_user_id::uuid,
    :phone_number,
    :connection_status,
    :disposition,
    COALESCE(:duration_seconds, 0),
    :recording_url,
    :notes,
    :scheduled_followup_at::timestamptz,
    now()
);
