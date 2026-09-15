SELECT id, company_id, referrer_name, email, phone, status,
       leads_count, rewards_paid_total, rewards_pending_total, joined_at, created_at, updated_at
FROM mkt_referrer
WHERE company_id = :company_id::uuid
  AND (:status::text IS NULL OR status = :status::text)
ORDER BY referrer_name ASC;
