/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.module;

import com.managemyopz.kernel.config.PlatformProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.PostConstruct;

/**
 * Scans classpath modules/&#42;/module.yaml (present on disk) and intersects
 * with platform.yaml modules.enabled (doc 01 §3). GET /api/v1/opzhub/meta/modules
 * exposes only what this returns — never the full vendor catalog.
 */
@Component
public class ModuleCatalog {

    private final PlatformProperties platformProperties;
    private final Map<String, Map<String, Object>> present = new LinkedHashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();

    @Autowired
    public ModuleCatalog(PlatformProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    @PostConstruct
    void scan() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:modules/*/module.yaml");
        Yaml yaml = new Yaml();
        for (Resource resource : resources) {
            try (InputStream in = resource.getInputStream()) {
                Map<String, Object> doc = yaml.load(in);
                if (doc != null && doc.get("id") instanceof String id) {
                    present.put(id, doc);
                }
            }
        }
        for (String id : platformProperties.getModules().getEnabled()) {
            if (present.containsKey(id)) {
                enabled.add(id);
            }
        }
    }

    public boolean isEnabled(String moduleId) {
        return enabled.contains(moduleId);
    }

    public Set<String> enabledModuleIds() {
        return enabled;
    }

    public Map<String, Map<String, Object>> presentModules() {
        return present;
    }
}
