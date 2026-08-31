package com.reconciliation.engine.common;

/**
 * Result of comparing one internal transaction against settlement data,
 * produced by the (not-yet-implemented) reconciliation engine and persisted
 * as a {@link com.reconciliation.engine.entity.ReconciliationLog}.
 *
 * MATCHED               - transaction and settlement agree on amount and status.
 * MISSING_SETTLEMENT    - transaction exists, no corresponding settlement found.
 * UNMATCHED_SETTLEMENT  - settlement exists, no corresponding transaction found.
 * AMOUNT_MISMATCH       - both exist, amounts differ.
 * STATUS_MISMATCH       - both exist, statuses disagree.
 * DUPLICATE_SETTLEMENT  - more than one settlement record found for the same
 *                         transaction reference.
 */
public enum ReconciliationStatus {
    MATCHED,
    MISSING_SETTLEMENT,
    UNMATCHED_SETTLEMENT,
    AMOUNT_MISMATCH,
    STATUS_MISMATCH,
    DUPLICATE_SETTLEMENT
}
