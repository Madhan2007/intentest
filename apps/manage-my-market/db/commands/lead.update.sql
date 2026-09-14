UPDATE mkt_lead
SET first_name = :first_name,
    last_name = :last_name,
    display_name = :display_name,
    company_name = :company_name,
    email = :email,
    phone = :phone,
    status = COALESCE(:status, status),
    source_detail = :source_detail,
    estimated_value = :estimated_value,
    priority = :priority,
    intent_score = :intent_score,
    referrer_id = :referrer_id::uuid,
    notes = :notes,
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
