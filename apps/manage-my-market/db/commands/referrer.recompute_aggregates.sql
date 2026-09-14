UPDATE mkt_referrer r
SET leads_count = (
        SELECT COUNT(*)
        FROM mkt_lead l
        WHERE l.referrer_id = r.id
    ),
    rewards_paid_total = COALESCE((
        SELECT SUM(amount)
        FROM mkt_referral_reward w
        WHERE w.referrer_id = r.id AND w.status = 'PAID'
    ), 0),
    rewards_pending_total = COALESCE((
        SELECT SUM(amount)
        FROM mkt_referral_reward w
        WHERE w.referrer_id = r.id AND w.status IN ('PENDING', 'APPROVED')
    ), 0),
    updated_at = now()
WHERE r.id = :referrer_id::uuid;
