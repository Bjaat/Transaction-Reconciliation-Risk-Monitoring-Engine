package com.reconciliation.engine.entity;

import com.reconciliation.engine.common.CreationAudit;
import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.common.RiskSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A risk indicator raised against a {@link Transaction} by the (not yet
 * implemented) risk engine.
 *
 * Unlike {@link Settlement}, this DOES use a real {@code @ManyToOne}
 * foreign key to {@link Transaction} — a risk flag only ever exists for a
 * transaction we already have in our system, so there's no "unmatched"
 * case to preserve here, and we get FK-enforced integrity for free.
 *
 * Duplicate-flag prevention: the migration adds a PARTIAL unique index —
 * {@code UNIQUE (transaction_id, rule_code) WHERE status = 'OPEN'} — rather
 * than a plain unique constraint. A plain constraint would permanently block
 * ever re-raising the same rule for the same transaction after the first
 * flag is resolved/dismissed; the partial index only prevents two
 * *concurrently open* flags for the same transaction/rule pair. JPA's
 * {@code @UniqueConstraint} cannot express a conditional/partial index, so
 * this constraint exists only in the Flyway migration, not as an annotation
 * here.
 */
@Entity
@Table(name = "risk_flags")
public class RiskFlag extends CreationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_risk_flags_transaction"))
    private Transaction transaction;

    /**
     * Identifier of the rule that raised this flag (e.g.
     * {@code "DUPLICATE_TRANSACTION"}, {@code "LARGE_AMOUNT"}). A plain
     * string rather than an enum: rules are implemented as individual
     * classes in a later phase, and new rules shouldn't require a schema
     * migration to add an enum value.
     */
    @Column(name = "rule_code", nullable = false, updatable = false, length = 64)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private RiskSeverity severity;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RiskFlagStatus status;

    protected RiskFlag() {
        // required by JPA
    }

    public RiskFlag(Transaction transaction, String ruleCode, RiskSeverity severity, String description,
                     LocalDateTime detectedAt, RiskFlagStatus status) {
        this.transaction = transaction;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.description = description;
        this.detectedAt = detectedAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public RiskSeverity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public RiskFlagStatus getStatus() {
        return status;
    }

    public void setStatus(RiskFlagStatus status) {
        this.status = status;
    }

    /**
     * No natural single-column business key exists here, so equality falls
     * back to the generated id. Two transient (unsaved) RiskFlag instances
     * are therefore only equal by reference — acceptable since flags aren't
     * used as value objects before being persisted.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiskFlag riskFlag)) return false;
        return id != null && Objects.equals(id, riskFlag.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RiskFlag{" +
                "id=" + id +
                ", transactionId=" + (transaction != null ? transaction.getId() : null) +
                ", ruleCode='" + ruleCode + '\'' +
                ", severity=" + severity +
                ", status=" + status +
                ", detectedAt=" + detectedAt +
                '}';
    }
}
