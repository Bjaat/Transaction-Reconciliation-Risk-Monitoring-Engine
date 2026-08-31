package com.reconciliation.engine.dto.transaction;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API-facing representation of a {@link Transaction}. Exposes both the
 * internal surrogate {@code id} (used as the REST resource identifier in
 * {@code /api/v1/transactions/{id}}) and the business
 * {@code transactionReference}, so clients that only know one or the other
 * can still correlate them.
 */
public class TransactionResponse {

    private final Long id;
    private final String transactionReference;
    private final String accountNumber;
    private final BigDecimal amount;
    private final String currency;
    private final TransactionType transactionType;
    private final TransactionStatus status;
    private final LocalDateTime transactionTimestamp;
    private final String externalReference;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public TransactionResponse(Long id, String transactionReference, String accountNumber, BigDecimal amount,
                                String currency, TransactionType transactionType, TransactionStatus status,
                                LocalDateTime transactionTimestamp, String externalReference,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.transactionReference = transactionReference;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.status = status;
        this.transactionTimestamp = transactionTimestamp;
        this.externalReference = externalReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Maps the JPA entity to its API-facing shape. Kept as a static factory
     * on the DTO itself (rather than a separate mapper class) since this is
     * a single, simple, one-directional mapping — introducing a dedicated
     * mapper class/interface for this alone would be unnecessary indirection.
     */
    public static TransactionResponse fromEntity(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getAccount().getAccountNumber(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getTransactionTimestamp(),
                transaction.getExternalReference(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public String getAccountNumber() {
        return accountNumber;
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

    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
