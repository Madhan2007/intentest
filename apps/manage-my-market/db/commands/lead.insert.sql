INSERT INTO mkt_lead (
    id, company_id, lead_code, lead_source_type, first_name, last_name,
    display_name, company_name, email, phone, status, source_detail,
    estimated_value, priority, intent_score, referrer_id, owner_user_id,
    created_by_user_id, notes, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :lead_code,
    :lead_source_type,
    :first_name,
    :last_name,
    :display_name,
    :company_name,
    :email,
    :phone,
    COALESCE(:status, 'NEW'),
    :source_detail,
    :estimated_value,
    :priority,
    :intent_score,
    :referrer_id::uuid,
    :owner_user_id::uuid,
    :created_by_user_id::uuid,
    :notes,
    now(),
    now()
);
