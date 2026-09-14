/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.LeadScore;

import java.util.Optional;

/**
 * Data access interface for Lead engagement score.
 */
public interface LeadScoreRepository {

    void upsert(LeadScore leadScore);
}
