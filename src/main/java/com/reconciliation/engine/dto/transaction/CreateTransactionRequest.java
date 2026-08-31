package com.reconciliation.engine.dto.transaction;

import com.reconciliation.engine.common.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request body for {@code POST /api/v1/transactions}.
 *
 * Deliberately does NOT accept a {@code status} — a newly created
 * transaction always starts as {@code PENDING}; letting a client dictate its
 * own initial status would let it fabricate history (e.g. submit a
 * transaction as already {@code COMPLETED}).
 */
public class CreateTransactionRequest {

    /**
     * Maps to {@code Transaction.transactionReference} — the business/
     * idempotency identifier the client (or upstream system) assigns.
     */
    @NotBlank(message = "transactionReference is required")
    @Size(max = 64, message = "transactionReference must be at most 64 characters")
    private String transactionReference;

    /**
     * Maps to {@code Account.accountNumber} — the business account
     * identifier, not the internal database id.
     */
    @NotBlank(message = "accountNumber is required")
    @Size(max = 64, message = "accountNumber must be at most 64 characters")
    private String accountNumber;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
    private String currency;

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    @NotNull(message = "transactionTimestamp is required")
    private LocalDateTime transactionTimestamp;

    /**
     * Optional upstream/processor reference — distinct from
     * {@code transactionReference}, matching the existing entity design.
     */
    @Size(max = 128, message = "externalReference must be at most 128 characters")
    private String externalReference;

    protected CreateTransactionRequest() {
        // required by Jackson
    }

    public CreateTransactionRequest(String transactionReference, String accountNumber, BigDecimal amount,
                                     String currency, TransactionType transactionType,
                                     LocalDateTime transactionTimestamp, String externalReference) {
        this.transactionReference = transactionReference;
        this.accountNumber = accountNumber;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.transactionTimestamp = transactionTimestamp;
        this.externalReference = externalReference;
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

    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
