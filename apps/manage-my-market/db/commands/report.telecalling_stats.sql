SELECT cl.agent_user_id,
       COUNT(*) AS total_calls,
       COUNT(*) FILTER (WHERE cl.call_outcome = 'CONNECTED') AS connected_calls,
       COUNT(*) FILTER (WHERE cl.call_outcome = 'INTERESTED') AS interested_calls,
       COUNT(*) FILTER (WHERE cl.call_outcome = 'NOT_INTERESTED') AS not_interested_calls,
       COUNT(*) FILTER (WHERE cl.call_outcome = 'NO_ANSWER') AS no_answer_calls,
       COALESCE(SUM(cl.call_duration_seconds), 0) AS total_duration_seconds,
       ROUND(COALESCE(AVG(cl.call_duration_seconds), 0), 1) AS avg_duration_seconds
FROM mkt_call_log cl
WHERE cl.company_id = :company_id::uuid
  AND (:agent_user_id::varchar IS NULL OR cl.agent_user_id = :agent_user_id)
  AND (:from_date::timestamptz IS NULL OR cl.called_at >= :from_date::timestamptz)
  AND (:to_date::timestamptz IS NULL OR cl.called_at <= :to_date::timestamptz)
GROUP BY cl.agent_user_id
ORDER BY total_calls DESC;
