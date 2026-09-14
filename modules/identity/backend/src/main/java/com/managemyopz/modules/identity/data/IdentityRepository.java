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

    /**
     * Finds a user by stored username within a company.
     *
     * @param username stored username
     * @param companyId company_information.id stored on the user
     * @return matching user when one row exists
     */
    Optional<User> findByUsernameAndCompanyId(String username, String companyId);

    /**
     * Finds a user by stored username.
     *
     * @param username stored username
     * @return matching user when one row exists
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by stored email.
     *
     * @param email stored email
     * @return matching user when one row exists
     */
    Optional<User> findByEmail(String email);

    /**
     * Counts stored identity accounts.
     *
     * @return number of rows in the identity table
     */
    long countUsers();

    /**
     * Inserts a user when the username is not already claimed.
     *
     * @param user account to insert
     * @return number of rows inserted
     */
    int createIfAbsent(User user);

    /**
     * Moves a legacy email-as-username administrator onto the current username
     * and email columns.
     *
     * @param username username to restore
     * @param email email currently stored as the username
     * @return number of rows updated
     */
    int restoreAdminUsername(String username, String email);

    /**
     * Assigns the bootstrap administrator email when that username exists.
     *
     * @param username administrator username
     * @param email administrator email
     * @return number of rows updated
     */
    int assignAdminEmail(String username, String email);

    /**
     * Assigns the administrator company when that username exists.
     *
     * @param username administrator username
     * @param companyId company_information.id
     * @return number of rows updated
     */
    int assignAdminCompany(String username, String companyId);
}
