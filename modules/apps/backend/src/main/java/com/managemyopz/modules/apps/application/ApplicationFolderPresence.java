/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Checks whether an application folder exists under apps/.
 */
package com.managemyopz.modules.apps.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An application is installable only when {@code apps/<app_key>} exists with
 * the frontend, backend, and mobile folders. Missing folders stay unlicensed.
 */
public class ApplicationFolderPresence {

    private static final Pattern APP_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,62}$");
    private static final String APPS_DIRECTORY = "apps";
    private static final String FRONTEND_DIRECTORY = "frontend";
    private static final String BACKEND_DIRECTORY = "backend";
    private static final String MOBILE_DIRECTORY = "mobile";
    private static final String CONTAINER_APPS_ROOT = "/home/tsuser/opzhub/apps";
    private static final String APPS_ROOT_ENVIRONMENT = "OPZHUB_APPS_ROOT";

    /**
     * Returns whether the application workspace folder is present.
     *
     * @param appKey catalog app_key
     * @return true when the apps folder exists
     */
    public boolean exists(String appKey) {
        if (appKey == null || !APP_KEY_PATTERN.matcher(appKey).matches()) {
            return false;
        }
        String normalizedKey = "manage-my-marketing".equals(appKey) ? "manage-my-market" : appKey;
        for (Path root : appRoots()) {
            Path applicationDirectory = root.resolve(normalizedKey);
            if (isApplicationDirectory(applicationDirectory)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isApplicationDirectory(Path applicationDirectory) {
        return Files.isDirectory(applicationDirectory)
            && Files.isDirectory(applicationDirectory.resolve(FRONTEND_DIRECTORY))
            && Files.isDirectory(applicationDirectory.resolve(BACKEND_DIRECTORY))
            && Files.isDirectory(applicationDirectory.resolve(MOBILE_DIRECTORY));
    }

    private static List<Path> appRoots() {
        List<Path> roots = new ArrayList<>();
        String configured = System.getenv(APPS_ROOT_ENVIRONMENT);
        if (configured != null && !configured.isBlank()) {
            roots.add(Path.of(configured));
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        roots.add(workingDirectory.resolve(APPS_DIRECTORY));
        Path parent = workingDirectory.getParent();
        if (parent != null) {
            roots.add(parent.resolve(APPS_DIRECTORY));
            Path grandparent = parent.getParent();
            if (grandparent != null) {
                roots.add(grandparent.resolve(APPS_DIRECTORY));
            }
        }
        roots.add(Path.of(CONTAINER_APPS_ROOT));
        return List.copyOf(roots);
    }
}
