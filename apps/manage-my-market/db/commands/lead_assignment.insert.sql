INSERT INTO mkt_lead_assignment (
    id, lead_id, assigned_by_user_id, assigned_to_user_id, transfer_reason, assigned_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :lead_id::uuid,
    :assigned_by_user_id::uuid,
    :assigned_to_user_id::uuid,
    :transfer_reason,
    now()
);
