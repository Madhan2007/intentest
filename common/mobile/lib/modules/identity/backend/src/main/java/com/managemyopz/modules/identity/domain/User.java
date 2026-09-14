/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.domain;

import java.util.List;

/** Value object mapped from DataClient rows; no JPA annotations (doc 04 §3). */
public record User(
    String id,
    String username,
    String displayName,
    String passwordHash,
    List<String> roles,
    boolean enabled
) {}
