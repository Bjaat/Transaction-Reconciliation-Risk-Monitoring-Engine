package com.reconciliation.engine.risk;

import com.reconciliation.engine.entity.Transaction;

import java.util.Optional;

/** One independently testable risk rule. */
public interface RiskRule {

    String code();

    Optional<RiskFinding> evaluate(Transaction transaction);
}
