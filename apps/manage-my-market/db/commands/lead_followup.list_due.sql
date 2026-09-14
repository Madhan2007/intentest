SELECT f.id, f.lead_id, f.reason, f.scheduled_at, f.assigned_agent_user_id,
       f.priority, f.status, f.notes, f.created_at, f.updated_at
FROM mkt_lead_followup f
JOIN mkt_lead l ON l.id = f.lead_id
WHERE l.company_id = :company_id::uuid
  AND (:assigned_agent_user_id::uuid IS NULL OR f.assigned_agent_user_id = :assigned_agent_user_id::uuid)
  AND f.status IN ('SCHEDULED', 'OVERDUE')
  AND f.scheduled_at <= :due_before::timestamptz
ORDER BY f.scheduled_at ASC;
