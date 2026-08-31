package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Building blocks for dynamic {@code Transaction} queries, combined by
 * {@code TransactionService} based on which filter criteria were actually
 * supplied. Chosen over one {@code findByXAndYAndZ(...)} repository method
 * per filter combination, which would otherwise grow combinatorially with
 * every filter added (8 optional filters here would mean up to 2^8 method
 * signatures).
 *
 * Each method returns {@code null} when its criterion is absent. The
 * service layer is responsible for skipping nulls when combining these
 * (see {@code TransactionService.buildSpecification}), rather than relying
 * on any particular null-handling behavior of {@code Specification.and}.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> hasAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("account").get("accountNumber"), accountNumber);
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Transaction> hasTransactionType(TransactionType transactionType) {
        if (transactionType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("transactionType"), transactionType);
    }

    public static Specification<Transaction> hasCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("currency"), currency);
    }

    public static Specification<Transaction> timestampFrom(LocalDateTime from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionTimestamp"), from);
    }

    public static Specification<Transaction> timestampTo(LocalDateTime to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionTimestamp"), to);
    }

    public static Specification<Transaction> amountAtLeast(BigDecimal minAmount) {
        if (minAmount == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount);
    }

    public static Specification<Transaction> amountAtMost(BigDecimal maxAmount) {
        if (maxAmount == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount);
    }
}
