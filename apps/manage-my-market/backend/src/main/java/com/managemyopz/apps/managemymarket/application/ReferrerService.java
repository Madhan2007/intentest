/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.ReferralRewardRepository;
import com.managemyopz.apps.managemymarket.data.ReferrerRepository;
import com.managemyopz.apps.managemymarket.domain.ReferralReward;
import com.managemyopz.apps.managemymarket.domain.Referrer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for referral partner management, rewards, and aggregate statistics.
 */
public class ReferrerService {

    private final ReferrerRepository referrerRepository;
    private final ReferralRewardRepository rewardRepository;

    public ReferrerService(ReferrerRepository referrerRepository, ReferralRewardRepository rewardRepository) {
        this.referrerRepository = referrerRepository;
        this.rewardRepository = rewardRepository;
    }

    public Referrer createReferrer(String companyId, String referrerName, String email, String phone) {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }
        if (referrerName == null || referrerName.isBlank()) {
            throw new IllegalArgumentException("referrer_name is required");
        }

        String id = UUID.randomUUID().toString();
        Referrer referrer = new Referrer(
            id,
            companyId,
            referrerName.trim(),
            email,
            phone,
            ManageMyMarketConstants.REFERRER_STATUS_ACTIVE,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.now(),
            Instant.now(),
            Instant.now()
        );
        referrerRepository.insert(referrer);
        return referrer;
    }

    public void updateReferrer(String id, String companyId, String referrerName, String email, String phone, String status) {
        Referrer referrer = new Referrer(
            id,
            companyId,
            referrerName,
            email,
            phone,
            status,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            Instant.now()
        );
        referrerRepository.update(referrer);
    }

    public List<Referrer> listReferrers(String companyId, String status) {
        return referrerRepository.listByCompany(companyId, status);
    }

    public Optional<Referrer> getById(String id, String companyId) {
        return referrerRepository.findById(id, companyId);
    }

    public void recomputeAggregates(String referrerId) {
        referrerRepository.recomputeAggregates(referrerId);
    }

    public ReferralReward createReward(String companyId, String referrerId, String leadId, BigDecimal amount, String payoutDetails) {
        Optional<Referrer> referrerOpt = referrerRepository.findById(referrerId, companyId);
        if (referrerOpt.isEmpty()) {
            throw new IllegalArgumentException("Referrer not found or does not belong to company: " + referrerId);
        }

        String id = UUID.randomUUID().toString();
        ReferralReward reward = new ReferralReward(
            id,
            referrerId,
            leadId,
            amount != null ? amount : BigDecimal.ZERO,
            ManageMyMarketConstants.REWARD_STATUS_PENDING,
            payoutDetails,
            Instant.now(),
            Instant.now()
        );
        rewardRepository.insert(reward);

        // Recompute referrer aggregates automatically
        referrerRepository.recomputeAggregates(referrerId);

        return reward;
    }

    public void updateRewardStatus(String rewardId, String referrerId, String status, String payoutDetails) {
        rewardRepository.updateStatus(rewardId, status, payoutDetails);

        // Recompute referrer aggregates
        referrerRepository.recomputeAggregates(referrerId);
    }

    public List<ReferralReward> listRewardsByReferrer(String referrerId) {
        return rewardRepository.listByReferrer(referrerId);
    }
}
