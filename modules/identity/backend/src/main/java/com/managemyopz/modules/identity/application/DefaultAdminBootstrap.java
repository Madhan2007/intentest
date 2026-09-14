/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Creates the initial PostgreSQL administrator without overwriting users.
 */
package com.managemyopz.modules.identity.application;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.modules.identity.data.IdentityRepository;
import com.managemyopz.modules.identity.data.SchemaCompanyDirectory;
import com.managemyopz.modules.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates the initial administrator for an empty PostgreSQL identity store
 *  and keeps the bootstrap username and email on existing administrator rows. */
public final class DefaultAdminBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAdminBootstrap.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String POSTGRES_DATABASE_TYPE = "postgres";
    private static final String BOOTSTRAP_PASSWORD_ENVIRONMENT_VARIABLE =
        "OPZHUB_BOOTSTRAP_ADMIN_PASSWORD";
    private static final String CREDENTIAL_FILE_PATH = "/etc/opzhub/bootstrap-admin-password";
    private static final String GENERATED_PASSWORD_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_";
    private static final int GENERATED_PASSWORD_LENGTH = 24;
    private static final String ADMIN_DISPLAY_NAME = "Administrator";

    private final IdentityRepository identityRepository;
    private final SchemaCompanyDirectory companyDirectory;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties platformProperties;

    /**
     * Creates the PostgreSQL administrator bootstrap service.
     *
     * @param identityRepository module data repository
     * @param companyDirectory company reference directory
     * @param passwordEncoder Argon2id password encoder
     * @param platformProperties resolved platform configuration
     */
    public DefaultAdminBootstrap(
        IdentityRepository identityRepository,
        SchemaCompanyDirectory companyDirectory,
        PasswordEncoder passwordEncoder,
        PlatformProperties platformProperties
    ) {
        this.identityRepository = identityRepository;
        this.companyDirectory = companyDirectory;
        this.passwordEncoder = passwordEncoder;
        this.platformProperties = platformProperties;
    }

    /**
     * Creates an administrator when PostgreSQL contains no user accounts, or
     * restores username `admin` and email `admin@technosprint.net` on the
     * existing bootstrap administrator.
     *
     * @return nothing
     */
    public void bootstrap() {
        String dbType = platformProperties.getDb().getType();
        LOGGER.info("Bootstrap: Checking database type: {}", dbType);
        if (!POSTGRES_DATABASE_TYPE.equalsIgnoreCase(dbType)) {
            LOGGER.info("Bootstrap: Skipped (not PostgreSQL: {})", dbType);
            return;
        }
        LOGGER.info("Bootstrap: Starting for PostgreSQL");

        long userCount = identityRepository.countUsers();
        if (userCount > 0) {
            LOGGER.info("Bootstrap: Skipped create (users exist: {})", userCount);
            alignExistingAdministrator();
            return;
        }
        LOGGER.info("Bootstrap: No users found, creating initial admin");

        String configuredPassword = System.getenv(BOOTSTRAP_PASSWORD_ENVIRONMENT_VARIABLE);
        boolean generatedPassword = configuredPassword == null || configuredPassword.isBlank();
        String initialPassword = generatedPassword ? generatePassword() : configuredPassword;
        LOGGER.info("Bootstrap: Initial password will be {}", generatedPassword ? "generated" : "configured");

        User administrator = new User(
            UUID.randomUUID().toString(),
            IdentityApplicationConstants.ADMIN_USERNAME,
            IdentityApplicationConstants.ADMIN_EMAIL,
            ADMIN_DISPLAY_NAME,
            passwordEncoder.encode(initialPassword),
            List.of(IdentityApplicationConstants.ROLE_ADMIN),
            true,
            resolveBootstrapCompanyId()
        );

        int created = identityRepository.createIfAbsent(administrator);
        if (created == 0) {
            LOGGER.info("Bootstrap: Skipped (concurrent create detected)");
            return;
        }
        LOGGER.info("Bootstrap: Initial admin created successfully");
        linkAdministratorCompany();

        if (generatedPassword) {
            writeGeneratedPassword(initialPassword);
            LOGGER.info("Bootstrap: Generated password written to {}", CREDENTIAL_FILE_PATH);
        } else {
            LOGGER.info("Bootstrap: Initial admin created with configured password");
        }
    }

    private void alignExistingAdministrator() {
        int restored = identityRepository.restoreAdminUsername(
            IdentityApplicationConstants.ADMIN_USERNAME,
            IdentityApplicationConstants.ADMIN_EMAIL
        );
        if (restored > 0) {
            LOGGER.info(
                "Bootstrap: Restored administrator username {} and email {}",
                IdentityApplicationConstants.ADMIN_USERNAME,
                IdentityApplicationConstants.ADMIN_EMAIL
            );
        } else {
            int assigned = identityRepository.assignAdminEmail(
                IdentityApplicationConstants.ADMIN_USERNAME,
                IdentityApplicationConstants.ADMIN_EMAIL
            );
            if (assigned > 0) {
                LOGGER.info(
                    "Bootstrap: Assigned administrator email {}",
                    IdentityApplicationConstants.ADMIN_EMAIL
                );
            }
        }
        linkAdministratorCompany();
    }

    private void linkAdministratorCompany() {
        String companyId = resolveBootstrapCompanyId();
        if (companyId == null) {
            LOGGER.warn(
                "Bootstrap: No company_information row matches the administrator email domain; login requires company/username or email after a company is stored"
            );
            return;
        }
        int linked = identityRepository.assignAdminCompany(
            IdentityApplicationConstants.ADMIN_USERNAME,
            companyId
        );
        if (linked > 0) {
            LOGGER.info("Bootstrap: Linked administrator to company {}", companyId);
        }
    }

    private String resolveBootstrapCompanyId() {
        return companyDirectory.findIdByReference(emailDomain(IdentityApplicationConstants.ADMIN_EMAIL))
            .orElse(null);
    }

    private static String emailDomain(String email) {
        int atIndex = email.lastIndexOf('@');
        return atIndex < 0 ? "" : email.substring(atIndex + 1);
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int index = 0; index < GENERATED_PASSWORD_LENGTH; index++) {
            int characterIndex = SECURE_RANDOM.nextInt(GENERATED_PASSWORD_ALPHABET.length());
            password.append(GENERATED_PASSWORD_ALPHABET.charAt(characterIndex));
        }
        return password.toString();
    }

    private void writeGeneratedPassword(String password) {
        try {
            Path credentialFile = Path.of(CREDENTIAL_FILE_PATH);
            Files.writeString(
                credentialFile,
                password + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            Files.setPosixFilePermissions(
                credentialFile,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write the initial administrator credential", exception);
        }
    }
}
