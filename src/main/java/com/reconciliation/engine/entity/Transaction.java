package com.reconciliation.engine.entity;

import com.reconciliation.engine.common.CreationAndUpdateAudit;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A single financial transaction against an {@link Account}.
 *
 * The {@link Account} association is {@code @ManyToOne(fetch = LAZY)} with
 * no cascade: a transaction's lifecycle is independent of its account's, so
 * saving/deleting an account must never silently save/delete transactions.
 * There is intentionally no {@code orphanRemoval} either — an "orphaned"
 * transaction (account deleted) should never happen in a financial system;
 * if it did, it should fail loudly (FK constraint violation), not silently
 * delete financial history.
 */
@Entity
@Table(
        name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transactions_reference", columnNames = "transaction_reference")
        }
)
public class Transaction extends CreationAndUpdateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Business-facing transaction identifier (e.g. a client-generated
     * idempotency key or an ID assigned at ingestion). This — not {@link #id}
     * — is what external systems (settlement feeds, clients) refer to, and
     * what {@link Settlement} and {@link ReconciliationLog} rows reference.
     */
    @Column(name = "transaction_reference", nullable = false, updatable = false, length = 64)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_transactions_account"))
    private Account account;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    /**
     * When the transaction actually occurred/was initiated (business/event
     * time) — distinct from {@code createdAt}, which is when the row was
     * written to our database. These can differ, e.g. for batch-imported or
     * delayed-ingestion transactions.
     */
    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    /**
     * Optional identifier from an upstream system (payment gateway, card
     * network, etc.), if this transaction originated externally. Not
     * unique — the same external system reference can plausibly appear on
     * retries/related transactions.
     */
    @Column(name = "external_reference", length = 128)
    private String externalReference;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(String transactionReference, Account account, BigDecimal amount, String currency,
                        TransactionType transactionType, TransactionStatus status,
                        LocalDateTime transactionTimestamp, String externalReference) {
        this.transactionReference = transactionReference;
        this.account = account;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.status = status;
        this.transactionTimestamp = transactionTimestamp;
        this.externalReference = externalReference;
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public String getExternalReference() {
        return externalReference;
    }

    /**
     * Equality based on the business key ({@code transactionReference}), for
     * the same reasons as {@link Account}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction that)) return false;
        return Objects.equals(transactionReference, that.transactionReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionReference);
    }

    @Override
    public String toString() {
        // Note: getAccount().getId() is safe on a lazy proxy — Hibernate
        // populates the id on the proxy without triggering a DB fetch.
        return "Transaction{" +
                "id=" + id +
                ", transactionReference='" + transactionReference + '\'' +
                ", accountId=" + (account != null ? account.getId() : null) +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", transactionType=" + transactionType +
                ", status=" + status +
                ", transactionTimestamp=" + transactionTimestamp +
                '}';
    }
}
