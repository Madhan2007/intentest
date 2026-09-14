/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.security;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Aspect enforcing @RequiresPermission annotations at the API level (doc 26 §4.1).
 */
@Aspect
@Component
public class PermissionCheckAspect {

    @Before("@annotation(rp)")
    public void checkPermission(JoinPoint jp, RequiresPermission rp) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof SessionAuthentication session)) {
            return;
        }

        Map<String, Map<String, String>> matrix = session.getMatrix();
        if (matrix == null || matrix.isEmpty()) {
            return;
        }

        Map<String, String> modulePerms = matrix.get(rp.module());
        if (modulePerms == null) {
            modulePerms = matrix.get("*");
        }
        if (modulePerms == null) {
            throw new AccessDeniedException("Permission denied for module: " + rp.module());
        }

        String allowedActions = modulePerms.get(rp.feature());
        if (allowedActions == null) {
            allowedActions = modulePerms.get("*");
        }
        if (allowedActions == null || !allowedActions.contains(rp.action())) {
            throw new AccessDeniedException("Permission denied: " + rp.module() + "." + rp.feature() + "." + rp.action());
        }
    }
}
