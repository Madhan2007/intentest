UPDATE mkt_lead_followup
SET reason = :reason,
    scheduled_at = :scheduled_at::timestamptz,
    assigned_agent_user_id = :assigned_agent_user_id::uuid,
    priority = :priority,
    status = COALESCE(:status, status),
    notes = :notes,
    updated_at = now()
WHERE id = :id::uuid;
