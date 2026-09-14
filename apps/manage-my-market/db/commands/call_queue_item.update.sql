UPDATE mkt_call_queue_item
SET priority = COALESCE(:priority, priority),
    status = COALESCE(:status, status),
    assigned_agent_user_id = :assigned_agent_user_id::uuid,
    next_call_scheduled_at = :next_call_scheduled_at::timestamptz,
    notes = :notes,
    updated_at = now()
WHERE id = :id::uuid;
