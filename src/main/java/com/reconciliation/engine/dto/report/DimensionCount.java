package com.reconciliation.engine.dto.report;

/** Count for one status, type, result, severity, or rule-code value. */
public record DimensionCount(String value, long count) {
}
