/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.List;

/**
 * Loads platform.yaml (doc 08 §1) before the Spring context refreshes and
 * inserts it as the highest-priority file-based property source — OS
 * environment variables (Spring's SystemEnvironmentPropertySource) still win,
 * matching the documented merge order:
 *   platform.yaml -> solution override -> /etc/opzhub override -> env vars -> CLI flags.
 * Candidate paths cover running from the repo root or from common/backend/.
 */
public class PlatformYamlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** Directory prefixes tried when a path is relative — repo root or common/backend cwd. */
    private static final String[] ROOT_PREFIXES = { "", "../../", "../" };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        String explicit = System.getenv("PLATFORM_CONFIG_PATH");
        if (explicit != null && !explicit.isBlank() && new File(explicit).isFile()) {
            addYamlSource("platformYaml", new FileSystemResource(explicit), loader, environment);
        } else {
            loadFirstExisting("platformYaml", "platform/config/platform.yaml", loader, environment);
        }

        // Solution override (doc 08 §1, item 2) — resolved after the base file so the
        // solution id can be read back from the environment we just built.
        String solutionId = environment.getProperty("solution.id");
        if (solutionId != null && !solutionId.isBlank()) {
            String overridePath = "solutions/" + solutionId + "/config/platform.override.yaml";
            loadFirstExisting("solutionOverrideYaml", overridePath, loader, environment);
        }

        // Site override (doc 08 §1, item 3) — one-time host path, kept across upgrades.
        File siteOverride = new File("/etc/opzhub/platform.override.yaml");
        if (siteOverride.exists()) {
            addYamlSource("siteOverrideYaml", new FileSystemResource(siteOverride), loader, environment);
        }
    }

    private void loadFirstExisting(String name, String relativePath, YamlPropertySourceLoader loader,
                                    ConfigurableEnvironment environment) {
        for (String prefix : ROOT_PREFIXES) {
            File file = new File(prefix + relativePath);
            if (file.isFile()) {
                addYamlSource(name, new FileSystemResource(file), loader, environment);
                return;
            }
        }
    }

    private void addYamlSource(String name, Resource resource, YamlPropertySourceLoader loader,
                                ConfigurableEnvironment environment) {
        try {
            List<PropertySource<?>> loaded = loader.load(name, resource);
            for (int i = loaded.size() - 1; i >= 0; i--) {
                environment.getPropertySources().addFirst(loaded.get(i));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load platform YAML: " + resource, e);
        }
    }
}
