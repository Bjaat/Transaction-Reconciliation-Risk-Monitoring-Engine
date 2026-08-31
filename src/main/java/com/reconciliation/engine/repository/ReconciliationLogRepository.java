package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.entity.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {

    List<ReconciliationLog> findByResult(ReconciliationStatus result);

    List<ReconciliationLog> findByTransactionReference(String transactionReference);
}
