package com.reconciliation.engine.exception;

/**
 * Thrown when a requested resource (transaction, account, etc.) doesn't
 * exist. Mapped to {@code 404 Not Found} by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
