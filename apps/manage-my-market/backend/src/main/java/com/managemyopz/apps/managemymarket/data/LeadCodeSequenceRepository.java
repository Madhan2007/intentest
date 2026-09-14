/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

/**
 * Data access interface for Lead code sequences.
 */
public interface LeadCodeSequenceRepository {

    long reserveNext(String companyId, String prefix);
}
