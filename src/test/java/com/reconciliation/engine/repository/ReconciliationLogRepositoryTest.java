package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.entity.ReconciliationLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ReconciliationLogRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReconciliationLogRepository reconciliationLogRepository;

    @Test
    void savesAMatchedResultWithNoAmountOrStatusDifference() {
        ReconciliationLog matched = new ReconciliationLog(
                "TXN-1", "EXT-SETL-1", ReconciliationStatus.MATCHED,
                new BigDecimal("100.0000"), new BigDecimal("100.0000"), BigDecimal.ZERO,
                "COMPLETED", "SETTLED", "Amounts and statuses match", LocalDateTime.now());

        ReconciliationLog saved = reconciliationLogRepository.saveAndFlush(matched);

        ReconciliationLog reloaded = reconciliationLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getResult()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(reloaded.getAmountDifference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void savesAMissingSettlementResultWithNullSettlementReference() {
        // A MISSING_SETTLEMENT row has no settlement to reference — this must
        // be persistable with settlement_reference = NULL, proving the
        // column is correctly nullable (unlike transaction_reference).
        ReconciliationLog missing = new ReconciliationLog(
                "TXN-2", null, ReconciliationStatus.MISSING_SETTLEMENT,
                new BigDecimal("75.0000"), null, null,
                "COMPLETED", null, "No settlement found for this transaction", LocalDateTime.now());

        ReconciliationLog saved = reconciliationLogRepository.saveAndFlush(missing);

        assertThat(saved.getId()).isNotNull();
        assertThat(reconciliationLogRepository.findById(saved.getId()).orElseThrow().getSettlementReference())
                .isNull();
    }

    @Test
    void findByResultFiltersCorrectly() {
        reconciliationLogRepository.saveAndFlush(new ReconciliationLog(
                "TXN-3", "EXT-3", ReconciliationStatus.AMOUNT_MISMATCH,
                new BigDecimal("100.0000"), new BigDecimal("90.0000"), new BigDecimal("10.0000"),
                "COMPLETED", "SETTLED", "Amount differs", LocalDateTime.now()));
        reconciliationLogRepository.saveAndFlush(new ReconciliationLog(
                "TXN-4", "EXT-4", ReconciliationStatus.MATCHED,
                new BigDecimal("50.0000"), new BigDecimal("50.0000"), BigDecimal.ZERO,
                "COMPLETED", "SETTLED", "Matches", LocalDateTime.now()));

        var mismatches = reconciliationLogRepository.findByResult(ReconciliationStatus.AMOUNT_MISMATCH);

        assertThat(mismatches).extracting(ReconciliationLog::getTransactionReference)
                .containsExactly("TXN-3");
    }

    @Test
    void findByTransactionReferenceReturnsAllLogsForThatTransaction() {
        reconciliationLogRepository.saveAndFlush(new ReconciliationLog(
                "TXN-5", "EXT-5", ReconciliationStatus.DUPLICATE_SETTLEMENT,
                new BigDecimal("20.0000"), new BigDecimal("20.0000"), BigDecimal.ZERO,
                "COMPLETED", "SETTLED", "Duplicate settlement detected", LocalDateTime.now()));

        var results = reconciliationLogRepository.findByTransactionReference("TXN-5");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getResult()).isEqualTo(ReconciliationStatus.DUPLICATE_SETTLEMENT);
    }
}
