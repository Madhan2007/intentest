UPDATE mkt_journey_enrollment
SET current_node_id = :current_node_id,
    status = COALESCE(:status, status),
    updated_at = now()
WHERE id = :id::uuid;
