package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.SettlementStatus;
import com.reconciliation.engine.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByTransactionReference(String transactionReference);

    Optional<Settlement> findByExternalSettlementReference(String externalSettlementReference);

    List<Settlement> findByStatus(SettlementStatus status);
}
