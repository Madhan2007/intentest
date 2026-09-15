SELECT j.id AS journey_id,
       j.name AS journey_name,
       je.current_node_id,
       COUNT(*) AS enrolled_count,
       COUNT(*) FILTER (WHERE je.status = 'ACTIVE') AS active_count,
       COUNT(*) FILTER (WHERE je.status = 'COMPLETED') AS completed_count,
       COUNT(*) FILTER (WHERE je.status = 'DROPPED') AS dropped_count
FROM mkt_journey j
JOIN mkt_journey_enrollment je ON j.id = je.journey_id
WHERE j.company_id = :company_id::uuid
  AND (:journey_id IS NULL OR j.id::text = :journey_id)
GROUP BY j.id, j.name, je.current_node_id
ORDER BY j.name, enrolled_count DESC;
