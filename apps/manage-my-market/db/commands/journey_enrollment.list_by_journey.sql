SELECT e.id, e.journey_id, e.lead_id, e.current_node_id, e.status, e.enrolled_at, e.updated_at,
       l.display_name AS lead_name, l.email AS lead_email
FROM mkt_journey_enrollment e
JOIN mkt_lead l ON l.id = e.lead_id
WHERE e.journey_id = :journey_id::uuid
  AND (:status IS NULL OR e.status = :status)
ORDER BY e.enrolled_at DESC;
