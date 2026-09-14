/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.AgentProfile;

import java.util.Optional;

/**
 * Data access interface for Marketing agent profiles.
 */
public interface AgentProfileRepository {

    void upsert(AgentProfile profile);

    Optional<AgentProfile> findByUserId(String agentUserId);
}
