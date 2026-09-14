UPDATE mkt_referral_reward
SET status = :status,
    payout_details = COALESCE(:payout_details, payout_details),
    updated_at = now()
WHERE id = :id::uuid;
