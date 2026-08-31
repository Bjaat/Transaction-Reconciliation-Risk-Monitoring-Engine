package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.SettlementStatus;
import com.reconciliation.engine.entity.Settlement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SettlementRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SettlementRepository settlementRepository;

    private Settlement newSettlement(String transactionReference, String externalReference, BigDecimal amount) {
        return new Settlement(transactionReference, externalReference, amount, "USD",
                SettlementStatus.SETTLED, LocalDateTime.now());
    }

    @Test
    void savesASettlementThatReferencesATransactionThatDoesNotExistLocally() {
        // This is the whole point of NOT using a foreign key here: a
        // settlement can legitimately arrive for a transaction reference
        // we've never seen (the UNMATCHED_SETTLEMENT case).
        Settlement saved = settlementRepository.saveAndFlush(
                newSettlement("TXN-UNKNOWN-999", "EXT-SETL-1", new BigDecimal("50.0000")));

        assertThat(saved.getId()).isNotNull();
        assertThat(settlementRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void rejectsDuplicateExternalSettlementReferences() {
        settlementRepository.saveAndFlush(newSettlement("TXN-1", "EXT-DUP", BigDecimal.TEN));

        assertThatThrownBy(() -> settlementRepository.saveAndFlush(
                newSettlement("TXN-1", "EXT-DUP", BigDecimal.ONE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByTransactionReferenceCanReturnMultipleRows() {
        // Multiple settlement rows for the same transaction reference is a
        // valid (if suspicious) state — it's exactly what the future
        // reconciliation engine flags as DUPLICATE_SETTLEMENT. The repository
        // must not silently hide that by only returning one.
        settlementRepository.saveAndFlush(newSettlement("TXN-SAME", "EXT-A", BigDecimal.TEN));
        settlementRepository.saveAndFlush(newSettlement("TXN-SAME", "EXT-B", BigDecimal.TEN));

        var results = settlementRepository.findByTransactionReference("TXN-SAME");

        assertThat(results).hasSize(2)
                .extracting(Settlement::getExternalSettlementReference)
                .containsExactlyInAnyOrder("EXT-A", "EXT-B");
    }

    @Test
    void findByStatusFiltersCorrectly() {
        settlementRepository.saveAndFlush(newSettlement("TXN-P1", "EXT-P1", BigDecimal.TEN));
        Settlement failed = new Settlement("TXN-P2", "EXT-P2", BigDecimal.TEN, "USD",
                SettlementStatus.FAILED, LocalDateTime.now());
        settlementRepository.saveAndFlush(failed);

        var results = settlementRepository.findByStatus(SettlementStatus.FAILED);

        assertThat(results).extracting(Settlement::getExternalSettlementReference)
                .containsExactly("EXT-P2");
    }
}
