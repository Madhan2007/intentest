SELECT id, company_id, queue_id, lead_id, agent_user_id, phone_number,
       connection_status, disposition, duration_seconds, recording_url, notes,
       scheduled_followup_at, created_at
FROM mkt_call_log
WHERE lead_id = :lead_id::uuid
ORDER BY created_at DESC;
