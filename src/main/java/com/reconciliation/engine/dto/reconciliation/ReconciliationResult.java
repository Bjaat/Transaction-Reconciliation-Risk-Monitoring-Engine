package com.reconciliation.engine.dto.reconciliation;

import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.entity.ReconciliationLog;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** API-facing representation of a {@link ReconciliationLog}. */
public class ReconciliationResult {

    private final Long id;
    private final String transactionReference;
    private final String settlementReference;
    private final ReconciliationStatus status;
    private final BigDecimal expectedAmount;
    private final BigDecimal actualAmount;
    private final String expectedCurrency;
    private final String actualCurrency;
    private final String expectedStatus;
    private final String actualStatus;
    private final String explanation;
    private final LocalDateTime reconciledAt;

    public ReconciliationResult(Long id, String transactionReference, String settlementReference,
                                 ReconciliationStatus status, BigDecimal expectedAmount, BigDecimal actualAmount,
                                 String expectedCurrency, String actualCurrency, String expectedStatus,
                                 String actualStatus, String explanation, LocalDateTime reconciledAt) {
        this.id = id;
        this.transactionReference = transactionReference;
        this.settlementReference = settlementReference;
        this.status = status;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
        this.expectedStatus = expectedStatus;
        this.actualStatus = actualStatus;
        this.explanation = explanation;
        this.reconciledAt = reconciledAt;
    }

    public static ReconciliationResult fromEntity(ReconciliationLog log) {
        return new ReconciliationResult(
                log.getId(), log.getTransactionReference(), log.getSettlementReference(), log.getResult(),
                log.getExpectedAmount(), log.getActualAmount(), log.getExpectedCurrency(), log.getActualCurrency(),
                log.getExpectedStatus(), log.getActualStatus(), log.getExplanation(), log.getReconciledAt());
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }

    public String getExpectedStatus() {
        return expectedStatus;
    }

    public String getActualStatus() {
        return actualStatus;
    }

    public String getExplanation() {
        return explanation;
    }

    public LocalDateTime getReconciledAt() {
        return reconciledAt;
    }
}
