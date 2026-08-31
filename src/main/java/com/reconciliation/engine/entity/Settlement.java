package com.reconciliation.engine.entity;

import com.reconciliation.engine.common.CreationAudit;
import com.reconciliation.engine.common.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A settlement record as reported by an external settlement/payment system.
 *
 * Deliberately holds {@code transactionReference} as a plain indexed string,
 * NOT a {@code @ManyToOne Transaction} foreign key. A settlement feed can
 * legitimately reference a transaction that:
 *   - hasn't been ingested yet (arrives out of order), or
 *   - never existed on our side at all (the exact "unmatched settlement"
 *     case the reconciliation engine exists to detect).
 * A NOT NULL foreign key would make it impossible to even persist those
 * rows. The trade-off is that referential integrity between Settlement and
 * Transaction is enforced by the reconciliation engine (a later phase), not
 * by the database — which is appropriate here, since "no matching
 * transaction" is a valid, expected state for this table, not a bug.
 */
@Entity
@Table(
        name = "settlements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settlements_external_reference", columnNames = "external_settlement_reference")
        }
)
public class Settlement extends CreationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The business transaction reference this settlement claims to settle.
     * Looked up against {@code transactions.transaction_reference} by the
     * reconciliation engine — see class Javadoc for why this isn't a FK.
     */
    @Column(name = "transaction_reference", nullable = false, length = 64)
    private String transactionReference;

    /**
     * Unique identifier assigned by the external settlement system. Used to
     * detect duplicate settlement feed entries (DUPLICATE_SETTLEMENT).
     */
    @Column(name = "external_settlement_reference", nullable = false, length = 64)
    private String externalSettlementReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "settlement_timestamp", nullable = false)
    private LocalDateTime settlementTimestamp;

    protected Settlement() {
        // required by JPA
    }

    public Settlement(String transactionReference, String externalSettlementReference, BigDecimal amount,
                       String currency, SettlementStatus status, LocalDateTime settlementTimestamp) {
        this.transactionReference = transactionReference;
        this.externalSettlementReference = externalSettlementReference;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.settlementTimestamp = settlementTimestamp;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getExternalSettlementReference() {
        return externalSettlementReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public LocalDateTime getSettlementTimestamp() {
        return settlementTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settlement that)) return false;
        return Objects.equals(externalSettlementReference, that.externalSettlementReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalSettlementReference);
    }

    @Override
    public String toString() {
        return "Settlement{" +
                "id=" + id +
                ", transactionReference='" + transactionReference + '\'' +
                ", externalSettlementReference='" + externalSettlementReference + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                ", settlementTimestamp=" + settlementTimestamp +
                '}';
    }
}
