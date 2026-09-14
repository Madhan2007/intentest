SELECT id, lead_id, assigned_by_user_id, assigned_to_user_id, transfer_reason, assigned_at
FROM mkt_lead_assignment
WHERE lead_id = :lead_id::uuid
ORDER BY assigned_at DESC;
