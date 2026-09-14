/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

/** Read-committed by default; posting/period-close may request stronger isolation (doc 04 §6). */
public enum Isolation {
    READ_COMMITTED,
    SERIALIZABLE
}
