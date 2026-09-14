INSERT INTO mkt_referrer (
    id, company_id, referrer_name, email, phone, status,
    leads_count, rewards_paid_total, rewards_pending_total, joined_at, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :company_id::uuid,
    :referrer_name,
    :email,
    :phone,
    COALESCE(:status, 'ACTIVE'),
    0,
    0,
    0,
    COALESCE(:joined_at::date, CURRENT_DATE),
    now(),
    now()
);
