/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-13
 * Description: Creates the initial platform administrator without overwriting existing accounts.
 */
package com.managemyopz.modules.identity.application;

import com.managemyopz.kernel.config.PlatformProperties;
import com.managemyopz.modules.identity.data.PlatformAdminRepository;
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

/** Creates the initial platform (central) administrator — doc 35 §2 — for an
 *  empty {@code platform_admin} table. Mirrors {@link DefaultAdminBootstrap}
 *  but has no company to link: platform admins never carry a company_id. */
public final class DefaultPlatformAdminBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlatformAdminBootstrap.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String POSTGRES_DATABASE_TYPE = "postgres";
    private static final String BOOTSTRAP_PASSWORD_ENVIRONMENT_VARIABLE =
        "OPZHUB_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD";
    private static final String CREDENTIAL_FILE_PATH = "/etc/opzhub/bootstrap-platform-admin-password";
    private static final String GENERATED_PASSWORD_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_";
    private static final int GENERATED_PASSWORD_LENGTH = 24;
    private static final String PLATFORM_ADMIN_USERNAME = "platformadmin";
    private static final String PLATFORM_ADMIN_DISPLAY_NAME = "Platform Administrator";

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformProperties platformProperties;

    /**
     * Creates the platform administrator bootstrap service.
     *
     * @param platformAdminRepository OPZMAIN platform_admin repository
     * @param passwordEncoder Argon2id password encoder
     * @param platformProperties resolved platform configuration
     */
    public DefaultPlatformAdminBootstrap(
        PlatformAdminRepository platformAdminRepository,
        PasswordEncoder passwordEncoder,
        PlatformProperties platformProperties
    ) {
        this.platformAdminRepository = platformAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.platformProperties = platformProperties;
    }

    /**
     * Creates a platform administrator when OPZMAIN's platform_admin table is
     * empty. Never overwrites an existing account or its password.
     */
    public void bootstrap() {
        String dbType = platformProperties.getDb().getType();
        if (!POSTGRES_DATABASE_TYPE.equalsIgnoreCase(dbType)) {
            LOGGER.info("Platform admin bootstrap: Skipped (not PostgreSQL: {})", dbType);
            return;
        }

        long adminCount = platformAdminRepository.countAdmins();
        if (adminCount > 0) {
            LOGGER.info("Platform admin bootstrap: Skipped create (admins exist: {})", adminCount);
            return;
        }
        LOGGER.info("Platform admin bootstrap: No admins found, creating initial platform admin");

        String configuredPassword = System.getenv(BOOTSTRAP_PASSWORD_ENVIRONMENT_VARIABLE);
        boolean generatedPassword = configuredPassword == null || configuredPassword.isBlank();
        String initialPassword = generatedPassword ? generatePassword() : configuredPassword;

        User admin = new User(
            UUID.randomUUID().toString(),
            PLATFORM_ADMIN_USERNAME,
            "",
            PLATFORM_ADMIN_DISPLAY_NAME,
            passwordEncoder.encode(initialPassword),
            List.of(IdentityApplicationConstants.ROLE_PLATFORM_ADMIN),
            true,
            null
        );

        int created = platformAdminRepository.createIfAbsent(admin);
        if (created == 0) {
            LOGGER.info("Platform admin bootstrap: Skipped (concurrent create detected)");
            return;
        }
        LOGGER.info("Platform admin bootstrap: Initial platform admin created successfully");

        if (generatedPassword) {
            writeGeneratedPassword(initialPassword);
            LOGGER.info("Platform admin bootstrap: Generated password written to {}", CREDENTIAL_FILE_PATH);
        } else {
            LOGGER.info("Platform admin bootstrap: Initial platform admin created with configured password");
        }
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
            throw new IllegalStateException("Unable to write the initial platform admin credential", exception);
        }
    }
}
