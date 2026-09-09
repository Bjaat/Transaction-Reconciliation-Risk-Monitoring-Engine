package com.reconciliation.engine.risk;

import com.reconciliation.engine.common.RiskSeverity;

/** A rule result before it is persisted as a risk flag. */
public record RiskFinding(String ruleCode, RiskSeverity severity, String description) {
}
