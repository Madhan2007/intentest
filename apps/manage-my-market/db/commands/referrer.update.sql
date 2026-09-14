UPDATE mkt_referrer
SET referrer_name = :referrer_name,
    email = :email,
    phone = :phone,
    status = COALESCE(:status, status),
    updated_at = now()
WHERE id = :id::uuid AND company_id = :company_id::uuid;
