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
import com.managemyopz.modules.identity.data.PlatformAdminRepository;
import com.managemyopz.modules.identity.data.SchemaCompanyDirectory;
import com.managemyopz.modules.identity.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local password login (doc 18 §2.1; platform admin — doc 35 §2). Identifiers
 * are one of three shapes, tried in this order:
 * <ol>
 *   <li>Contains {@code @} — email; domain must appear in
 *       {@code company_information.company_references}.</li>
 *   <li>Contains {@code /} — {@code companyReference/username}; the prefix
 *       must appear in {@code company_information.company_references}.</li>
 *   <li>Bare username (neither) — resolved against {@code platform_admin}
 *       in OPZMAIN. No company routing applies to this session.</li>
 * </ol>
 * A tenant user's stored row must carry the matching {@code company_id}.
 */
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Pattern COMPANY_REFERENCE_PATTERN =
        Pattern.compile("^[a-z0-9][a-z0-9.-]{0,127}$");
    private static final Pattern USERNAME_PATTERN =
        Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
    private static final String EMAIL_MARKER = "@";

    private final String dummyHash;
    private final IdentityRepository repository;
    private final PlatformAdminRepository platformAdminRepository;
    private final SchemaCompanyDirectory companyDirectory;
    private final PasswordEncoder passwordEncoder;
    private final CacheClient cacheClient;
    private final PlatformProperties platformProperties;
    private final ModuleCatalog moduleCatalog;
    private final ObjectMapper objectMapper;

    public AuthService(
        IdentityRepository repository,
        PlatformAdminRepository platformAdminRepository,
        SchemaCompanyDirectory companyDirectory,
        PasswordEncoder passwordEncoder,
        CacheClient cacheClient,
        PlatformProperties platformProperties,
        ModuleCatalog moduleCatalog,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.platformAdminRepository = platformAdminRepository;
        this.companyDirectory = companyDirectory;
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
     * @param username supplied company/username or email
     * @param password supplied plaintext password, used only for Argon2id verification
     * @return session details required by the authenticated client
     * @throws BadCredentialsException when the user cannot be authenticated
     */
    public LoginResult login(String username, String password) {
        String loginIdentifier = normalizeLoginIdentifier(username);
        String suppliedPassword = password == null ? "" : password;
        Optional<User> locatedUser = findUserByLoginIdentifier(loginIdentifier);
        User user = locatedUser.orElse(null);
        String hashToCheck = (user == null) ? dummyHash : user.passwordHash();
        boolean matches = passwordEncoder.matches(suppliedPassword, hashToCheck);

        if (user == null || !user.enabled() || !matches) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        Map<String, Map<String, String>> matrix = buildMatrix(user);
        String token = UUID.randomUUID().toString();
        long ttl = platformProperties.getSecurity().getSessionTtlSeconds();

        SessionData session = new SessionData(
            user.id(), user.username(), user.displayName(), user.roles(), matrix, user.companyId());
        try {
            cacheClient.set(
                IdentityApplicationConstants.SESSION_CACHE_KEY_PREFIX + token,
                objectMapper.writeValueAsString(session),
                Duration.ofSeconds(ttl)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist session", e);
        }

        return new LoginResult(
            token, ttl, user.id(), user.username(), user.displayName(), user.roles(), matrix, user.companyId());
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

    private Map<String, Map<String, String>> buildMatrix(User user) {
        Map<String, Map<String, String>> matrix = new LinkedHashMap<>();
        if (user.roles().contains(IdentityApplicationConstants.ROLE_PLATFORM_ADMIN)) {
            // Platform admin (doc 35 §2): scoped ONLY to company-setup — no
            // tenant module, no wildcard, no company_id on the session.
            matrix.put(
                IdentityApplicationConstants.COMPANY_SETUP_MODULE_ID,
                Map.of(
                    IdentityApplicationConstants.WILDCARD,
                    IdentityApplicationConstants.FULL_ACCESS
                )
            );
            return matrix;
        }
        if (user.roles().contains(IdentityApplicationConstants.ROLE_ADMIN)) {
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

    private static String normalizeLoginIdentifier(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves company/username, email, or a bare platform-admin username.
     *
     * @param loginIdentifier normalized identifier
     * @return matching user when the identifier resolves to exactly one account
     */
    private Optional<User> findUserByLoginIdentifier(String loginIdentifier) {
        if (loginIdentifier.isBlank()) {
            return Optional.empty();
        }
        if (loginIdentifier.contains(EMAIL_MARKER)) {
            return findUserByEmail(loginIdentifier);
        }
        if (loginIdentifier.contains(IdentityApplicationConstants.COMPANY_USERNAME_SEPARATOR)) {
            return findUserByCompanyUsername(loginIdentifier);
        }
        return findPlatformAdminByUsername(loginIdentifier);
    }

    /**
     * Resolves a bare username (no "/" and no "@") against platform_admin in
     * OPZMAIN — the central admin login that manages companies and licenses
     * without ever routing to a company database (doc 35 §2).
     *
     * @param username normalized bare username
     * @return matching platform admin when one row exists
     */
    private Optional<User> findPlatformAdminByUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return Optional.empty();
        }
        return platformAdminRepository.findByUsername(username);
    }

    private Optional<User> findUserByEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return Optional.empty();
        }
        int atIndex = email.lastIndexOf(EMAIL_MARKER);
        String domain = email.substring(atIndex + 1);
        Optional<String> companyId = companyDirectory.findIdByReference(domain);
        if (companyId.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> user = repository.findByEmail(email);
        if (user.isEmpty() || !companyId.get().equals(user.get().companyId())) {
            return Optional.empty();
        }
        return user;
    }

    private Optional<User> findUserByCompanyUsername(String loginIdentifier) {
        String separator = IdentityApplicationConstants.COMPANY_USERNAME_SEPARATOR;
        int separatorIndex = loginIdentifier.indexOf(separator);
        if (separatorIndex <= 0 || separatorIndex != loginIdentifier.lastIndexOf(separator)) {
            return Optional.empty();
        }
        String companyReference = loginIdentifier.substring(0, separatorIndex);
        String username = loginIdentifier.substring(separatorIndex + 1);
        if (!COMPANY_REFERENCE_PATTERN.matcher(companyReference).matches()
                || !USERNAME_PATTERN.matcher(username).matches()) {
            return Optional.empty();
        }
        Optional<String> companyId = companyDirectory.findIdByReference(companyReference);
        if (companyId.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByUsernameAndCompanyId(username, companyId.get());
    }
}
