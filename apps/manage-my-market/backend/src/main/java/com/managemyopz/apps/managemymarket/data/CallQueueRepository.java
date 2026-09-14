/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallQueue;

import java.util.List;
import java.util.Optional;

/**
 * Data access interface for Call queues.
 */
public interface CallQueueRepository {

    void insert(CallQueue queue);

    List<CallQueue> listByCompany(String companyId);

    Optional<CallQueue> findById(String id, String companyId);
}
