/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

/** Authenticated principal backed by a cache-stored session (doc 18 §5), not Spring HttpSession. */
public class SessionAuthentication extends AbstractAuthenticationToken {

    private final String userId;
    private final String username;
    private final String displayName;
    private final Map<String, Map<String, String>> matrix;
    private final String companyId;

    public SessionAuthentication(String userId, String username, String displayName,
                                  List<String> roles, Map<String, Map<String, String>> matrix,
                                  String companyId) {
        super(roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.matrix = matrix;
        this.companyId = companyId;
        setAuthenticated(true);
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public Map<String, Map<String, String>> getMatrix() { return matrix; }
    public String getCompanyId() { return companyId; }

    @Override
    public Object getCredentials() { return null; }

    @Override
    public Object getPrincipal() { return username; }
}
