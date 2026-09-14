/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.LeadCodeSequenceRepository;

/**
 * Service for atomic lead code generation without race conditions.
 */
public class LeadCodeSequenceService {

    public static final String DEFAULT_PREFIX = "LED";

    private final LeadCodeSequenceRepository sequenceRepository;

    public LeadCodeSequenceService(LeadCodeSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    public String reserveNextCode(String companyId, String prefix) {
        String effectivePrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim().toUpperCase() : DEFAULT_PREFIX;
        long seq = sequenceRepository.reserveNext(companyId, effectivePrefix);
        return String.format("%s-%05d", effectivePrefix, seq);
    }

    public String previewNextCode(String companyId, String prefix) {
        String effectivePrefix = (prefix != null && !prefix.isBlank()) ? prefix.trim().toUpperCase() : DEFAULT_PREFIX;
        return String.format("%s-XXXXX", effectivePrefix);
    }
}
