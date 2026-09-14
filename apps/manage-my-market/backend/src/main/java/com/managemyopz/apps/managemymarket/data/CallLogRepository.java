/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-14
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.apps.managemymarket.data;

import com.managemyopz.apps.managemymarket.domain.CallLog;

import java.util.List;

/**
 * Data access interface for Call logs.
 */
public interface CallLogRepository {

    void insert(CallLog callLog);

    List<CallLog> listByLead(String leadId);
}
