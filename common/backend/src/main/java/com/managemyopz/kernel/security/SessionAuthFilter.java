/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.managemyopz.kernel.cache.client.CacheClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads the opzhub session cookie or Bearer header, resolves it against
 * CacheClient, and populates the SecurityContext (doc 18 §6). Missing/expired
 * session is left unauthenticated — SecurityConfig decides which paths need one.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "opzhub_session";

    private final CacheClient cacheClient;
    private final ObjectMapper objectMapper;

    public SessionAuthFilter(CacheClient cacheClient, ObjectMapper objectMapper) {
        this.cacheClient = cacheClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        extractToken(request).flatMap(token -> cacheClient.get("session:" + token)).ifPresent(json -> {
            try {
                SessionData session = objectMapper.readValue(json, SessionData.class);
                SecurityContextHolder.getContext().setAuthentication(new SessionAuthentication(
                    session.userId(), session.username(), session.displayName(),
                    session.roles(), session.matrix(), session.companyId()));
            } catch (Exception ignored) {
                // Corrupt session payload — treat as unauthenticated rather than failing the request.
            }
        });
        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return Optional.of(auth.substring(7));
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
