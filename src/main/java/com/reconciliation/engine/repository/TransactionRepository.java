package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByTransactionTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
