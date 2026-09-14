/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.EmailTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Email templates.
 */
public interface EmailTemplateRepository {

    void insert(EmailTemplate template);

    void update(EmailTemplate template);

    List<EmailTemplate> listByCompany(String companyId);

    Optional<EmailTemplate> findById(String id, String companyId);
}
