package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.entity.RiskFlag;
import com.reconciliation.engine.repository.projection.DimensionCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RiskFlagRepository extends JpaRepository<RiskFlag, Long> {

    List<RiskFlag> findByTransactionId(Long transactionId);

    List<RiskFlag> findBySeverity(RiskSeverity severity);

    boolean existsByTransactionIdAndRuleCodeAndStatus(Long transactionId, String ruleCode, RiskFlagStatus status);

    List<RiskFlag> findByTransactionTransactionReferenceOrderByDetectedAtDesc(String transactionReference);

    @Query("""
            select f.status as groupValue, count(f) as total
            from RiskFlag f
            where f.detectedAt >= :from
              and f.detectedAt <= :to
            group by f.status
            order by f.status
            """)
    List<DimensionCountProjection> summarizeStatus(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    @Query("""
            select f.severity as groupValue, count(f) as total
            from RiskFlag f
            where f.detectedAt >= :from
              and f.detectedAt <= :to
            group by f.severity
            order by f.severity
            """)
    List<DimensionCountProjection> summarizeSeverity(@Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to);

    @Query("""
            select f.ruleCode as groupValue, count(f) as total
            from RiskFlag f
            where f.detectedAt >= :from
              and f.detectedAt <= :to
            group by f.ruleCode
            order by f.ruleCode
            """)
    List<DimensionCountProjection> summarizeRule(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);
}
