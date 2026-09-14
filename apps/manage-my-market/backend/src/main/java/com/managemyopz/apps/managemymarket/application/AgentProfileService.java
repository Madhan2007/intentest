/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.application;

import com.managemyopz.apps.managemymarket.data.AgentProfileRepository;
import com.managemyopz.apps.managemymarket.domain.AgentProfile;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing agent marketing profiles.
 */
public class AgentProfileService {

    private final AgentProfileRepository profileRepository;

    public AgentProfileService(AgentProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public AgentProfile upsertProfile(String agentUserId, String companyId, String marketingType, String linkedPersonId) {
        if (agentUserId == null || agentUserId.isBlank()) {
            throw new IllegalArgumentException("agent_user_id is required");
        }
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("company_id is required");
        }

        AgentProfile profile = new AgentProfile(
            agentUserId,
            companyId,
            marketingType != null ? marketingType.toUpperCase() : ManageMyMarketConstants.AGENT_TYPE_TELECALLER,
            linkedPersonId,
            Instant.now(),
            Instant.now()
        );
        profileRepository.upsert(profile);
        return profile;
    }

    public Optional<AgentProfile> getProfileByUserId(String agentUserId) {
        return profileRepository.findByUserId(agentUserId);
    }
}
