package com.reconciliation.engine.common;

/**
 * Processing status of a {@link com.reconciliation.engine.entity.Transaction}.
 *
 * PENDING     - created, not yet processed.
 * PROCESSING  - actively being processed (e.g. sent to an external processor).
 * COMPLETED   - processed successfully.
 * FAILED      - processing failed (declined, insufficient funds, etc.).
 * REVERSED    - was completed, then reversed/rolled back.
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVERSED
}
