/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.data.client;

/** Mapped kernel errors — features never see driver exceptions (doc 07 §11). */
public class DataClientException extends RuntimeException {
    private final String kind;

    public DataClientException(String kind, String message) {
        super(message);
        this.kind = kind;
    }

    public DataClientException(String kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public String getKind() { return kind; }

    public static DataClientException notFound(String message) { return new DataClientException("not_found", message); }
    public static DataClientException conflict(String message) { return new DataClientException("conflict", message); }
    public static DataClientException unavailable(String message, Throwable cause) { return new DataClientException("data_unavailable", message, cause); }
    public static DataClientException unsupported(String message) { return new DataClientException("unsupported_capability", message); }
}
