package com.reconciliation.engine.common;

/**
 * Severity of a detected {@link com.reconciliation.engine.entity.RiskFlag}.
 * Ordered low to high; ordinal order is intentional and can be relied on for
 * simple "at least MEDIUM" style comparisons later, but rule code should
 * still compare by name where clarity matters more than ordering.
 */
public enum RiskSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
