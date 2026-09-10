package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.entity.ReconciliationLog;
import com.reconciliation.engine.repository.projection.DimensionCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {

    List<ReconciliationLog> findByResult(ReconciliationStatus result);

    List<ReconciliationLog> findByTransactionReference(String transactionReference);

    /** Newest-first history for a transaction — used by the reconciliation history endpoint. */
    List<ReconciliationLog> findByTransactionReferenceOrderByReconciledAtDesc(String transactionReference);

    @Query("""
            select r.result as groupValue, count(r) as total
            from ReconciliationLog r
            where r.reconciledAt >= :from
              and r.reconciledAt <= :to
            group by r.result
            order by r.result
            """)
    List<DimensionCountProjection> summarizeResults(@Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);
}
