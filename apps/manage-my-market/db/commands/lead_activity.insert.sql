INSERT INTO mkt_lead_activity (
    id, lead_id, activity_type, title, description,
    old_value, new_value, related_call_log_id, related_followup_id,
    performed_by_user_id, created_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :lead_id::uuid,
    COALESCE(:activity_type, 'NOTE'),
    :title,
    :description,
    :old_value,
    :new_value,
    :related_call_log_id::uuid,
    :related_followup_id::uuid,
    :performed_by_user_id::uuid,
    now()
);
