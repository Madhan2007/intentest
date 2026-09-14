/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.modules.identity.domain.User;

import java.util.Optional;

/** Module-owned data port — kernel never imports this (doc 04 §3, §5). */
public interface IdentityRepository {
    Optional<User> findByUsername(String username);

    void ensureUserTable();

    long countUsers();

    int createIfAbsent(User user);
}
