/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.admin;

import com.managemyopz.kernel.module.ConditionalOnModule;
import org.springframework.context.annotation.Configuration;

/**
 * Placeholder shared module — no settings screens exist yet (doc 01 §1.2).
 * Registers only when modules/admin is present and enabled, proving the
 * same discovery path as identity for a second module.
 */
@Configuration
@ConditionalOnModule("admin")
public class AdminAutoConfiguration {
}
