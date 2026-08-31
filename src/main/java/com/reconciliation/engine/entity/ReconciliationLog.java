package com.reconciliation.engine.entity;

import com.reconciliation.engine.common.CreationAudit;
import com.reconciliation.engine.common.ReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The persisted result of comparing one transaction against settlement data,
 * produced by the (not yet implemented) reconciliation engine.
 *
 * Like {@link Settlement}, this references {@link Transaction} and
 * {@link Settlement} by business reference string, not foreign key — for
 * the same reason: a MISSING_SETTLEMENT or UNMATCHED_SETTLEMENT row is, by
 * definition, describing a case where one side of the pair doesn't exist,
 * so a settlement_reference here is nullable and neither reference is FK
 * constrained.
 */
@Entity
@Table(name = "reconciliation_logs")
public class ReconciliationLog extends CreationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    /**
     * Nullable: a MISSING_SETTLEMENT result has no settlement to reference.
     */
    @Column(name = "settlement_reference", length = 64)
    private String settlementReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private ReconciliationStatus result;

    @Column(name = "expected_amount", precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 4)
    private BigDecimal actualAmount;

    /**
     * Stored rather than derived on read because a reconciliation log is a
     * point-in-time audit record: it must keep reporting the difference that
     * was true when reconciliation ran, even if the underlying transaction
     * or settlement rows are later modified.
     */
    @Column(name = "amount_difference", precision = 19, scale = 4)
    private BigDecimal amountDifference;

    @Column(name = "expected_status", length = 20)
    private String expectedStatus;

    @Column(name = "actual_status", length = 20)
    private String actualStatus;

    @Column(name = "explanation", length = 1000)
    private String explanation;

    @Column(name = "reconciled_at", nullable = false)
    private LocalDateTime reconciledAt;

    protected ReconciliationLog() {
        // required by JPA
    }

    public ReconciliationLog(String transactionReference, String settlementReference, ReconciliationStatus result,
                              BigDecimal expectedAmount, BigDecimal actualAmount, BigDecimal amountDifference,
                              String expectedStatus, String actualStatus, String explanation,
                              LocalDateTime reconciledAt) {
        this.transactionReference = transactionReference;
        this.settlementReference = settlementReference;
        this.result = result;
        this.expectedAmount = expectedAmount;
        this.actualAmount = actualAmount;
        this.amountDifference = amountDifference;
        this.expectedStatus = expectedStatus;
        this.actualStatus = actualStatus;
        this.explanation = explanation;
        this.reconciledAt = reconciledAt;
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

    public ReconciliationStatus getResult() {
        return result;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public BigDecimal getAmountDifference() {
        return amountDifference;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReconciliationLog that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReconciliationLog{" +
                "id=" + id +
                ", transactionReference='" + transactionReference + '\'' +
                ", settlementReference='" + settlementReference + '\'' +
                ", result=" + result +
                ", expectedAmount=" + expectedAmount +
                ", actualAmount=" + actualAmount +
                ", amountDifference=" + amountDifference +
                ", reconciledAt=" + reconciledAt +
                '}';
    }
}
