package com.reconciliation.engine.dto.reconciliation;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/reconciliations}.
 *
 * Identifies the transaction by its business {@code transactionReference},
 * not the internal numeric id used by the Transaction API — reconciliation
 * inherently works across system boundaries (matching against an external
 * settlement feed), and {@code transactionReference} is the identifier both
 * sides actually share.
 */
public class ReconcileTransactionRequest {

    @NotBlank(message = "transactionReference is required")
    private String transactionReference;

    protected ReconcileTransactionRequest() {
        // required by Jackson
    }

    public ReconcileTransactionRequest(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getTransactionReference() {
        return transactionReference;
    }
}
