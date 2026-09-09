package com.reconciliation.engine.dto.risk;

import java.util.List;

public class RiskEvaluationResult {

    private final String transactionReference;
    private final int rulesEvaluated;
    private final List<RiskFlagResponse> flagsCreated;

    public RiskEvaluationResult(String transactionReference, int rulesEvaluated, List<RiskFlagResponse> flagsCreated) {
        this.transactionReference = transactionReference;
        this.rulesEvaluated = rulesEvaluated;
        this.flagsCreated = List.copyOf(flagsCreated);
    }

    public String getTransactionReference() { return transactionReference; }
    public int getRulesEvaluated() { return rulesEvaluated; }
    public List<RiskFlagResponse> getFlagsCreated() { return flagsCreated; }
}
