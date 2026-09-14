SELECT id, referrer_id, lead_id, amount, status, payout_details, created_at, updated_at
FROM mkt_referral_reward
WHERE referrer_id = :referrer_id::uuid
ORDER BY created_at DESC;
