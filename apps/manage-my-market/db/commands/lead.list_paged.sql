SELECT id, company_id, lead_code, lead_source_type, first_name, last_name,
       display_name, company_name, email, phone, status, source_detail,
       estimated_value, priority, intent_score, referrer_id, owner_user_id,
       created_by_user_id, notes, created_at, updated_at
FROM mkt_lead
WHERE company_id = :company_id::uuid
  AND (:status::text IS NULL OR status = :status::text)
  AND (:lead_source_type::text IS NULL OR lead_source_type = :lead_source_type::text)
  AND (:owner_user_id::text IS NULL OR owner_user_id::text = :owner_user_id::text)
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset;
