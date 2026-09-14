SELECT id, company_id, lead_code, lead_source_type, first_name, last_name,
       display_name, company_name, email, phone, status, source_detail,
       estimated_value, priority, intent_score, referrer_id, owner_user_id,
       created_by_user_id, notes, created_at, updated_at
FROM mkt_lead
WHERE id = :id::uuid AND company_id = :company_id::uuid;
