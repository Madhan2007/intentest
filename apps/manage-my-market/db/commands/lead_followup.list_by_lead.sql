SELECT id, lead_id, reason, scheduled_at, assigned_agent_user_id,
       priority, status, notes, created_at, updated_at
FROM mkt_lead_followup
WHERE lead_id = :lead_id::uuid
ORDER BY scheduled_at ASC;
