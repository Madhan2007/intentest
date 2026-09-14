/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds the merged platform.yaml (doc 08). Only the keys this implementation
 * pass actually reads are modeled; unknown YAML keys are ignored by Spring's
 * relaxed binding, matching "unknown module keys are ignored" (doc 05 §10).
 */
@Configuration
@ConfigurationProperties(prefix = "")
public class PlatformProperties {

    private Solution solution = new Solution();
    private Db db = new Db();
    private Map<String, Map<String, Object>> databases = new LinkedHashMap<>();
    private Cache cache = new Cache();
    private Broker broker = new Broker();
    private Security security = new Security();
    private Modules modules = new Modules();
    private Http http = new Http();

    public Solution getSolution() { return solution; }
    public void setSolution(Solution solution) { this.solution = solution; }
    public Db getDb() { return db; }
    public void setDb(Db db) { this.db = db; }
    /** Named additional Postgres connections (e.g. OPZUSER, OPZMAIN), keyed exactly
     *  as declared in platform.yaml. Empty unless a solution opts into multi-database
     *  routing; the {@code db} connection above always remains the default. */
    public Map<String, Map<String, Object>> getDatabases() { return databases; }
    public void setDatabases(Map<String, Map<String, Object>> databases) { this.databases = databases; }
    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }
    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Modules getModules() { return modules; }
    public void setModules(Modules modules) { this.modules = modules; }
    public Http getHttp() { return http; }
    public void setHttp(Http http) { this.http = http; }

    public static class Solution {
        private String id = "kernel-dev";
        private String displayName = "ManageMyOpz Kernel";
        private String profile = "dev";
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getProfile() { return profile; }
        public void setProfile(String profile) { this.profile = profile; }
    }

    public static class Db {
        private String type = "memory";
        private Map<String, Object> postgres = new LinkedHashMap<>();
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Map<String, Object> getPostgres() { return postgres; }
        public void setPostgres(Map<String, Object> postgres) { this.postgres = postgres; }
    }

    public static class Cache {
        private String type = "memory";
        private Map<String, Object> valkey = new LinkedHashMap<>();
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Map<String, Object> getValkey() { return valkey; }
        public void setValkey(Map<String, Object> valkey) { this.valkey = valkey; }
    }

    public static class Broker {
        private String type = "memory";
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class Security {
        private boolean allowOpenDev = false;
        private long sessionTtlSeconds = 28800;
        private Auth auth = new Auth();
        public boolean isAllowOpenDev() { return allowOpenDev; }
        public void setAllowOpenDev(boolean allowOpenDev) { this.allowOpenDev = allowOpenDev; }
        public long getSessionTtlSeconds() { return sessionTtlSeconds; }
        public void setSessionTtlSeconds(long sessionTtlSeconds) { this.sessionTtlSeconds = sessionTtlSeconds; }
        public Auth getAuth() { return auth; }
        public void setAuth(Auth auth) { this.auth = auth; }
    }

    public static class Auth {
        private List<String> methods = new ArrayList<>(List.of("password"));
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
    }

    public static class Modules {
        private List<String> enabled = new ArrayList<>();
        private List<String> disabled = new ArrayList<>();
        public List<String> getEnabled() { return enabled; }
        public void setEnabled(List<String> enabled) { this.enabled = enabled; }
        public List<String> getDisabled() { return disabled; }
        public void setDisabled(List<String> disabled) { this.disabled = disabled; }
    }

    public static class Http {
        private Map<String, Object> opzhubBeApp = new LinkedHashMap<>();
        private Map<String, Object> opzhubBeCore = new LinkedHashMap<>();
        private Map<String, Object> pub = new LinkedHashMap<>();
        public Map<String, Object> getOpzhubBeApp() { return opzhubBeApp; }
        public void setOpzhubBeApp(Map<String, Object> opzhubBeApp) { this.opzhubBeApp = opzhubBeApp; }
        public Map<String, Object> getOpzhubBeCore() { return opzhubBeCore; }
        public void setOpzhubBeCore(Map<String, Object> opzhubBeCore) { this.opzhubBeCore = opzhubBeCore; }
        public Map<String, Object> getPublic() { return pub; }
        public void setPublic(Map<String, Object> pub) { this.pub = pub; }
    }
}
