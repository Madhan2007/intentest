/*
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz Java coding standards.
 */
package com.managemyopz.kernel.web;

import com.managemyopz.kernel.data.client.DataClientException;
import com.managemyopz.kernel.data.schema.SchemaValidationException;
import com.managemyopz.kernel.module.ModuleNotPresentException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kernel error contract (doc 24): safe `msg` + copyable correlation_id.
 * Driver text never reaches the SPA/Flutter in prod.
 */
@RestControllerAdvice
public class KernelExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KernelExceptionHandler.class);

    @ExceptionHandler(DataClientException.class)
    public ResponseEntity<ApiEnvelope<Void>> onDataClient(DataClientException e, HttpServletRequest request) {
        HttpStatus status = switch (e.getKind()) {
            case "not_found" -> HttpStatus.NOT_FOUND;
            case "conflict" -> HttpStatus.CONFLICT;
            case "data_unavailable" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(envelope(e.getKind(), e.getMessage(), request));
    }

    @ExceptionHandler(ModuleNotPresentException.class)
    public ResponseEntity<ApiEnvelope<Void>> onModuleNotPresent(ModuleNotPresentException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope("module_not_present", e.getMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> onAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope("forbidden", "Not permitted.", request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> onValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.fail(
            new ApiEnvelope.ApiError("validation_failed", "validation_failed", "Invalid request parameters.", null, fields),
            correlationId == null ? "" : correlationId.toString()
        ));
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ResponseEntity<ApiEnvelope<Void>> onSchemaValidation(SchemaValidationException e, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (SchemaValidationException.FieldError fe : e.getFieldErrors()) {
            fields.put(fe.field(), fe.message());
        }
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiEnvelope.fail(
            new ApiEnvelope.ApiError("validation_failed", "validation_failed", "Invalid request parameters.", null, fields),
            correlationId == null ? "" : correlationId.toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> onAny(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(envelope("internal_error", "Something went wrong. Please retry.", request));
    }

    private ApiEnvelope<Void> envelope(String code, String msg, HttpServletRequest request) {
        Object correlationId = request.getAttribute(CorrelationFilter.MDC_KEY);
        return ApiEnvelope.fail(new ApiEnvelope.ApiError(code, code, msg, null, null),
            correlationId == null ? "" : correlationId.toString());
    }
}
