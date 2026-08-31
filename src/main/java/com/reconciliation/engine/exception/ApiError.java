package com.reconciliation.engine.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response body returned by every handler in
 * {@link GlobalExceptionHandler}, so API consumers see one consistent shape
 * regardless of what went wrong.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final Map<String, String> fieldErrors;

    public ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public ApiError(int status, String error, String message, String path) {
        this(status, error, message, path, null);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
