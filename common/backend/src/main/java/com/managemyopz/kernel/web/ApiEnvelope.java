/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard kernel response wrapper (doc 04 §4):
 * { "ok": true, "data": {}, "error": null, "correlation_id": "..." }
 */
public record ApiEnvelope<T>(boolean ok, T data, ApiError error, @JsonProperty("correlation_id") String correlationId) {

    public static <T> ApiEnvelope<T> ok(T data, String correlationId) {
        return new ApiEnvelope<>(true, data, null, correlationId);
    }

    public static <T> ApiEnvelope<T> fail(ApiError error, String correlationId) {
        return new ApiEnvelope<>(false, null, error, correlationId);
    }

    public record ApiError(String code, String kind, String msg, String hint, Object fields) {}
}
