SELECT id, company_id, lead_code, lead_source_type, first_name, last_name,
       display_name, company_name, email, phone, status, source_detail,
       estimated_value, priority, intent_score, referrer_id, owner_user_id,
       created_by_user_id, notes, created_at, updated_at
FROM mkt_lead
WHERE company_id = :company_id::uuid
  AND (:status IS NULL OR status = :status)
  AND (:lead_source_type IS NULL OR lead_source_type = :lead_source_type)
  AND (:owner_user_id::uuid IS NULL OR owner_user_id = :owner_user_id::uuid)
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset;
