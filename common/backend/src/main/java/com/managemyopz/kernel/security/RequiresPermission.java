/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the RBAC permission required to execute a controller method (doc 26 §4.1).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    /** Module id from module.yaml, e.g. "manage-my-market" */
    String module();
    /** Feature id from module.yaml access.features, e.g. "led". Default "*" */
    String feature() default "*";
    /** Single action letter: v | c | u | d | a */
    String action();
}
