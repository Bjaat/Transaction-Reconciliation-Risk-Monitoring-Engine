package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByTransactionTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
