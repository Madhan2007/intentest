/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadScoreRepository;
import com.managemyopz.apps.managemymarket.domain.LeadScore;

import java.time.Instant;

/**
 * Service for managing lead engagement/quality scores.
 */
public class LeadScoreService {

    private final LeadScoreRepository scoreRepository;

    public LeadScoreService(LeadScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    public void upsertScore(String leadId, int score, String breakdownJson) {
        LeadScore leadScore = new LeadScore(
            leadId,
            score,
            breakdownJson,
            Instant.now()
        );
        scoreRepository.upsert(leadScore);
    }
}
