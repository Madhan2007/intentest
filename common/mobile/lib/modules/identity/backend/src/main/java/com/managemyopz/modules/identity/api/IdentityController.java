/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.api;

import com.managemyopz.kernel.security.SessionAuthFilter;
import com.managemyopz.kernel.security.SessionAuthentication;
import com.managemyopz.kernel.web.ApiEnvelope;
import com.managemyopz.kernel.web.CorrelationFilter;
import com.managemyopz.modules.identity.api.dto.LoginRequest;
import com.managemyopz.modules.identity.application.AuthService;
import com.managemyopz.modules.identity.application.BadCredentialsException;
import com.managemyopz.modules.identity.application.LoginResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Login/session/logout (doc 18 §4). Only `password` is wired in this pass;
 * OAuth2/face/fingerprint routes are reserved for a later drop.
 */
@RestController
@RequestMapping("/api/v1/opzhub/identity")
public class IdentityController {

    private final AuthService authService;

    public IdentityController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> login(@RequestBody LoginRequest request,
                                                                    HttpServletRequest httpRequest,
                                                                    HttpServletResponse httpResponse) {
        try {
            LoginResult result = authService.login(request.username(), request.password());

            Cookie cookie = new Cookie(SessionAuthFilter.COOKIE_NAME, result.token());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge((int) result.ttlSeconds());
            httpResponse.addCookie(cookie);

            return ResponseEntity.ok(ApiEnvelope.ok(sessionPayload(result), correlationId(httpRequest)));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiEnvelope.fail(
                new ApiEnvelope.ApiError("invalid_credentials", "unauthenticated", "Invalid username or password.", null, null),
                correlationId(httpRequest)));
        }
    }

    @GetMapping("/session")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> session(Authentication authentication,
                                                                       HttpServletRequest request) {
        if (!(authentication instanceof SessionAuthentication session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", session.getUserId());
        user.put("n", session.getDisplayName());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("a", session.getMatrix());
        return ResponseEntity.ok(ApiEnvelope.ok(data, correlationId(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiEnvelope<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        extractToken(request).ifPresent(authService::logout);
        Cookie cookie = new Cookie(SessionAuthFilter.COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(ApiEnvelope.ok(null, correlationId(request)));
    }

    private Map<String, Object> sessionPayload(LoginResult result) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", result.userId());
        user.put("n", result.displayName());
        user.put("r", result.roles());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("a", result.matrix());
        data.put("token", result.token());
        return data;
    }

    private java.util.Optional<String> extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return java.util.Optional.of(auth.substring(7));
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SessionAuthFilter.COOKIE_NAME.equals(cookie.getName())) {
                    return java.util.Optional.of(cookie.getValue());
                }
            }
        }
        return java.util.Optional.empty();
    }

    private String correlationId(HttpServletRequest request) {
        Object v = request.getAttribute(CorrelationFilter.MDC_KEY);
        return v == null ? "" : v.toString();
    }
}
