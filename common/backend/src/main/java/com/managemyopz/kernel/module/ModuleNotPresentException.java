/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.module;

/**
 * Thrown when a caller requires a port whose owning module folder is not
 * present in this pack (doc 04 §5). Callers should catch this and hide the
 * dependent UI action rather than fail the request.
 */
public class ModuleNotPresentException extends RuntimeException {
    public ModuleNotPresentException(String moduleId) {
        super("Module not present in this pack: " + moduleId);
    }
}
