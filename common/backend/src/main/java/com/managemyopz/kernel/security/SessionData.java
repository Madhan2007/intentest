/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.security;

import java.util.List;
import java.util.Map;

/** JSON shape stored at CacheClient key session:{token} (doc 18 §5). */
public record SessionData(
    String userId,
    String username,
    String displayName,
    List<String> roles,
    Map<String, Map<String, String>> matrix,
    String companyId
) {}
