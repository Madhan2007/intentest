/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.application;

import java.util.Map;

/** Result handed to the controller: session token + the compact login payload (doc 18 §4). */
public record LoginResult(
    String token,
    long ttlSeconds,
    String userId,
    String username,
    String displayName,
    java.util.List<String> roles,
    Map<String, Map<String, String>> matrix
) {}
