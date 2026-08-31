package com.reconciliation.engine.exception;

/**
 * Thrown for a request that is well-formed but not valid in context — e.g.
 * an unrecognized filter value, or (in a later phase) an illegal status
 * transition. Distinct from Bean Validation failures (which
 * {@link GlobalExceptionHandler} handles separately via
 * {@code MethodArgumentNotValidException}). Mapped to {@code 400 Bad Request}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
