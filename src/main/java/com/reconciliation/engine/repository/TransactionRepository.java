package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.repository.projection.CurrencyAmountProjection;
import com.reconciliation.engine.repository.projection.DimensionCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    boolean existsByTransactionReference(String transactionReference);

    long countByExternalReference(String externalReference);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByTransactionTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("""
            select t.status as groupValue, count(t) as total
            from Transaction t
            where t.transactionTimestamp >= :from
              and t.transactionTimestamp <= :to
            group by t.status
            order by t.status
            """)
    List<DimensionCountProjection> summarizeStatus(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    @Query("""
            select t.transactionType as groupValue, count(t) as total
            from Transaction t
            where t.transactionTimestamp >= :from
              and t.transactionTimestamp <= :to
            group by t.transactionType
            order by t.transactionType
            """)
    List<DimensionCountProjection> summarizeType(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    @Query("""
            select t.currency as currency, count(t) as transactionCount, sum(t.amount) as totalAmount
            from Transaction t
            where t.transactionTimestamp >= :from
              and t.transactionTimestamp <= :to
            group by t.currency
            order by t.currency
            """)
    List<CurrencyAmountProjection> summarizeAmountsByCurrency(@Param("from") LocalDateTime from,
                                                               @Param("to") LocalDateTime to);
}
