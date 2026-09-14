SELECT id, lead_id, activity_type, title, description,
       old_value, new_value, related_call_log_id, related_followup_id,
       performed_by_user_id, created_at
FROM mkt_lead_activity
WHERE lead_id = :lead_id::uuid
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset;
