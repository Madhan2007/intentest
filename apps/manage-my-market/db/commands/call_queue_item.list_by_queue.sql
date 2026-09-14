SELECT i.id, i.queue_id, i.lead_id, i.priority, i.status, i.assigned_agent_user_id,
       i.call_attempts, i.last_dialed_at, i.next_call_scheduled_at, i.notes, i.created_at, i.updated_at,
       l.display_name AS lead_name, l.phone AS lead_phone, l.company_name AS lead_company
FROM mkt_call_queue_item i
JOIN mkt_lead l ON l.id = i.lead_id
WHERE i.queue_id = :queue_id::uuid
  AND (:status IS NULL OR i.status = :status)
  AND (:assigned_agent_user_id::uuid IS NULL OR i.assigned_agent_user_id = :assigned_agent_user_id::uuid)
ORDER BY i.priority DESC NULLS LAST, i.created_at ASC;
