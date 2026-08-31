package com.reconciliation.engine.common;

/**
 * Status of a settlement record as reported by the external settlement/payment
 * system. This mirrors {@link TransactionStatus} conceptually but is kept as
 * a separate enum because the two systems are independent: a transaction's
 * internal status and its settlement status can legitimately disagree, and
 * detecting that disagreement (STATUS_MISMATCH) is a core reconciliation rule.
 */
public enum SettlementStatus {
    PENDING,
    SETTLED,
    FAILED,
    REVERSED
}
