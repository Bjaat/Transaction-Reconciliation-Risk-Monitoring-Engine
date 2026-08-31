package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.entity.RiskFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskFlagRepository extends JpaRepository<RiskFlag, Long> {

    List<RiskFlag> findByTransactionId(Long transactionId);

    List<RiskFlag> findBySeverity(RiskSeverity severity);
}
