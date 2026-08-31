package com.reconciliation.engine.dto.transaction;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carries the optional filter criteria for {@code GET /api/v1/transactions}
 * from the controller to the service layer. All fields are optional (null =
 * "don't filter on this"). This is a plain internal carrier, not a
 * validated {@code @RequestBody} DTO — query parameters are bound and
 * validated individually in the controller so that an invalid one (e.g. an
 * unrecognized status) produces a specific, clear error message rather than
 * a generic Spring type-conversion failure.
 */
public class TransactionFilter {

    private final String accountNumber;
    private final TransactionStatus status;
    private final TransactionType transactionType;
    private final String currency;
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;

    public TransactionFilter(String accountNumber, TransactionStatus status, TransactionType transactionType,
                              String currency, LocalDateTime from, LocalDateTime to,
                              BigDecimal minAmount, BigDecimal maxAmount) {
        this.accountNumber = accountNumber;
        this.status = status;
        this.transactionType = transactionType;
        this.currency = currency;
        this.from = from;
        this.to = to;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public LocalDateTime getTo() {
        return to;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }
}
