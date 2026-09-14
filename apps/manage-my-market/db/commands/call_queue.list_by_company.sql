SELECT id, company_id, queue_name, description, priority, status, created_by_user_id, created_at, updated_at
FROM mkt_call_queue
WHERE company_id = :company_id::uuid
ORDER BY priority DESC, created_at DESC;
