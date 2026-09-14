/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Source file governed by the ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.kernel.module.ModuleCatalog;
import com.managemyopz.kernel.security.SessionData;
import com.managemyopz.modules.identity.data.IdentityRepository;
import com.managemyopz.modules.identity.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Local password login (doc 18 §2.1). License-server preflight, OAuth2,
 * face and fingerprint are not wired in this first implementation pass —
 * `security.auth.methods` only lists `password` in platform.yaml.
 */
public class AuthService {

    // Precomputed Argon2 hash of a value nobody can type, used only for constant-time
    // rejection of unknown usernames (doc 18 §2.1 "dummy-hash for timing").
    private final String dummyHash;

    private final IdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final CacheClient cacheClient;
    private final PlatformProperties platformProperties;
    private final ModuleCatalog moduleCatalog;
    private final ObjectMapper objectMapper;

    public AuthService(IdentityRepository repository, PasswordEncoder passwordEncoder, CacheClient cacheClient,
                        PlatformProperties platformProperties, ModuleCatalog moduleCatalog, ObjectMapper objectMapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.cacheClient = cacheClient;
        this.platformProperties = platformProperties;
        this.moduleCatalog = moduleCatalog;
        this.objectMapper = objectMapper;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Authenticates a user and creates an opaque, time-limited session.
     *
     * @param username supplied account name
     * @param password supplied plaintext password, used only for Argon2id verification
     * @return session details required by the authenticated client
     * @throws BadCredentialsException when the user cannot be authenticated
     */
    public LoginResult login(String username, String password) {
        User user = repository.findByUsername(username).orElse(null);
        String hashToCheck = (user == null) ? dummyHash : user.passwordHash();
        boolean matches = passwordEncoder.matches(password == null ? "" : password, hashToCheck);

        if (user == null || !user.enabled() || !matches) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        Map<String, Map<String, String>> matrix = buildMatrix(user);
        String token = UUID.randomUUID().toString();
        long ttl = platformProperties.getSecurity().getSessionTtlSeconds();

        SessionData session = new SessionData(user.id(), user.username(), user.displayName(), user.roles(), matrix);
        try {
            cacheClient.set(
                IdentityApplicationConstants.SESSION_CACHE_KEY_PREFIX + token,
                objectMapper.writeValueAsString(session),
                Duration.ofSeconds(ttl)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist session", e);
        }

        return new LoginResult(token, ttl, user.id(), user.username(), user.displayName(), user.roles(), matrix);
    }

    /**
     * Removes the session represented by an opaque token.
     *
     * @param token opaque session token to invalidate; blank values are ignored
     */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            cacheClient.delete(IdentityApplicationConstants.SESSION_CACHE_KEY_PREFIX + token);
        }
    }

    /**
     * Minimal RBAC placeholder: full grant/feature evaluation (doc 18 §3) arrives
     * once a sold application ships features. Today only `identity` + `admin` are
     * installed. Three roles map to increasingly permissive access matrices:
     * - admin: full control of admin module
     * - superuser: full control of admin, view-only to others
     * - user: view-only across all modules
     */
    private Map<String, Map<String, String>> buildMatrix(User user) {
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        if (user.roles().contains(IdentityApplicationConstants.ROLE_ADMIN)
                && moduleCatalog.isEnabled(IdentityApplicationConstants.ADMIN_MODULE_ID)) {
            matrix.put(
                IdentityApplicationConstants.ADMIN_MODULE_ID,
                Map.of(
                    IdentityApplicationConstants.WILDCARD,
                    IdentityApplicationConstants.FULL_ACCESS
                )
            );
        } else if (user.roles().contains(IdentityApplicationConstants.ROLE_SUPERUSER)) {
            if (moduleCatalog.isEnabled(IdentityApplicationConstants.ADMIN_MODULE_ID)) {
                matrix.put(
                    IdentityApplicationConstants.ADMIN_MODULE_ID,
                    Map.of(
                        IdentityApplicationConstants.WILDCARD,
                        IdentityApplicationConstants.FULL_ACCESS
                    )
                );
            }
            matrix.put(
                IdentityApplicationConstants.WILDCARD,
                Map.of(
                    IdentityApplicationConstants.WILDCARD,
                    IdentityApplicationConstants.VIEW_ACCESS
                )
            );
        } else if (user.roles().contains(IdentityApplicationConstants.ROLE_USER)) {
            matrix.put(
                IdentityApplicationConstants.WILDCARD,
                Map.of(
                    IdentityApplicationConstants.WILDCARD,
                    IdentityApplicationConstants.VIEW_ACCESS
                )
            );
        }
        return matrix;
    }
}
