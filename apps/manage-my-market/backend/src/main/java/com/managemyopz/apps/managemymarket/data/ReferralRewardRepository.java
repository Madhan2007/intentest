/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.ReferralReward;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Referral rewards.
 */
public interface ReferralRewardRepository {

    void insert(ReferralReward reward);

    void updateStatus(String id, String status, String payoutDetails);

    List<ReferralReward> listByReferrer(String referrerId);

    Optional<ReferralReward> findById(String id);
}
