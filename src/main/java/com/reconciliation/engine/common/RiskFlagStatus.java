package com.reconciliation.engine.common;

/**
 * Resolution status of a {@link com.reconciliation.engine.entity.RiskFlag}.
 *
 * OPEN            - raised, not yet reviewed.
 * RESOLVED        - reviewed and confirmed as a genuine issue that was handled.
 * DISMISSED       - reviewed and intentionally ignored (e.g. accepted risk).
 * FALSE_POSITIVE  - reviewed and determined the rule fired incorrectly.
 */
public enum RiskFlagStatus {
    OPEN,
    RESOLVED,
    DISMISSED,
    FALSE_POSITIVE
}
