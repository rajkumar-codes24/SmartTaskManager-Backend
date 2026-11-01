package com.smarttaskmanager.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API error response body.
 *
 * <p>Ensures that all client-facing error responses have a consistent structure,
 * proper HTTP status, machine-readable application error code, and optional
 * field-level validation details.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        List<FieldErrorDetail> validationErrors
) {
    public static ErrorResponse of(int status, String error, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, errorCode, message, path, null);
    }

    public static ErrorResponse ofValidation(int status, String error, String errorCode, String message, String path, List<FieldErrorDetail> validationErrors) {
        return new ErrorResponse(Instant.now(), status, error, errorCode, message, path, validationErrors);
    }

    public record FieldErrorDetail(
            String field,
            Object rejectedValue,
            String message
    ) {}
}
