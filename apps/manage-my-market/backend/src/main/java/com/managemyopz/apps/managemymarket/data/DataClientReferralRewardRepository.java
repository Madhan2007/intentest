/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.ReferralReward;
import com.managemyopz.kernel.data.client.DataClient;
import com.managemyopz.kernel.data.client.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DataClient-backed repository for Referral reward operations.
 */
public class DataClientReferralRewardRepository implements ReferralRewardRepository {

    private final DataClient dataClient;

    public DataClientReferralRewardRepository(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void insert(ReferralReward reward) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", reward.id());
        params.put("referrer_id", reward.referrerId());
        params.put("lead_id", reward.leadId());
        params.put("amount", reward.amount());
        params.put("status", reward.status());
        params.put("payout_details", reward.payoutDetails());

        dataClient.execute(MarketDataConstants.REFERRAL_REWARD_INSERT, params);
    }

    @Override
    public void updateStatus(String id, String status, String payoutDetails) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("status", status);
        params.put("payout_details", payoutDetails);

        dataClient.execute(MarketDataConstants.REFERRAL_REWARD_UPDATE_STATUS, params);
    }

    @Override
    public List<ReferralReward> listByReferrer(String referrerId) {
        return dataClient.query(MarketDataConstants.REFERRAL_REWARD_LIST_BY_REFERRER, Map.of("referrer_id", referrerId)).stream()
            .map(DataClientReferralRewardRepository::rewardFromRow)
            .toList();
    }

    @Override
    public Optional<ReferralReward> findById(String id) {
        return Optional.empty();
    }

    private static ReferralReward rewardFromRow(Row row) {
        return new ReferralReward(
            row.get("id") != null ? row.get("id").toString() : null,
            row.get("referrer_id") != null ? row.get("referrer_id").toString() : null,
            row.get("lead_id") != null ? row.get("lead_id").toString() : null,
            row.getDecimal("amount"),
            row.getString("status"),
            row.getString("payout_details"),
            row.getInstant("created_at"),
            row.getInstant("updated_at")
        );
    }
}
