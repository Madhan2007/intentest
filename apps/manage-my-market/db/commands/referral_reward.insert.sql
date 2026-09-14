INSERT INTO mkt_referral_reward (
    id, referrer_id, lead_id, amount, status, payout_details, created_at, updated_at
) VALUES (
    COALESCE(:id::uuid, gen_random_uuid()),
    :referrer_id::uuid,
    :lead_id::uuid,
    :amount,
    COALESCE(:status, 'PENDING'),
    :payout_details,
    now(),
    now()
);
