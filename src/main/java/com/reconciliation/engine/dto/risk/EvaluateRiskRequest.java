package com.reconciliation.engine.dto.risk;

import jakarta.validation.constraints.NotBlank;

public class EvaluateRiskRequest {

    @NotBlank(message = "transactionReference is required")
    private String transactionReference;

    protected EvaluateRiskRequest() {
    }

    public EvaluateRiskRequest(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getTransactionReference() {
        return transactionReference;
    }
}
