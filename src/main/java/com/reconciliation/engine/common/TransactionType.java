package com.reconciliation.engine.common;

/**
 * The kind of financial movement a {@link com.reconciliation.engine.entity.Transaction}
 * represents. Kept intentionally small and generic (not payment-rail-specific)
 * since this is a simulation of a general ledger-style transaction flow.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    PAYMENT,
    REFUND
}
