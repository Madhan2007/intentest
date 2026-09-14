/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-13
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.modules.identity.data;

import com.managemyopz.modules.identity.domain.User;

import java.util.Optional;

/** Module-owned data port for platform_admin (OPZMAIN) — doc 35 §2.
 *  Distinct from {@link IdentityRepository}, which is bound to OPZUSER. */
public interface PlatformAdminRepository {

    /**
     * Finds a platform admin by stored username.
     *
     * @param username stored username
     * @return matching admin, mapped as a company-less {@link User}, when one row exists
     */
    Optional<User> findByUsername(String username);

    /**
     * Counts stored platform admin accounts.
     *
     * @return number of rows in platform_admin
     */
    long countAdmins();

    /**
     * Inserts a platform admin when the username is not already claimed.
     *
     * @param admin account to insert
     * @return number of rows inserted
     */
    int createIfAbsent(User admin);
}
