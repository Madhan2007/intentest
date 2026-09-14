SELECT r.id,
       r.referrer_code,
       r.name,
       r.referrer_type,
       r.total_referrals,
       r.successful_conversions,
       r.total_rewards_earned,
       r.pending_rewards,
       r.status
FROM mkt_referrer r
WHERE r.company_id = :company_id::uuid
  AND (:status::varchar IS NULL OR r.status = :status)
ORDER BY r.successful_conversions DESC, r.total_referrals DESC
LIMIT :limit::integer;
