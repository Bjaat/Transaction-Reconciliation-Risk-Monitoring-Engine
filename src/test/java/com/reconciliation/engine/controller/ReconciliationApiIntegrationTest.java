package com.reconciliation.engine.controller;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.SettlementStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.reconciliation.BatchReconciliationResult;
import com.reconciliation.engine.dto.reconciliation.ReconcileTransactionRequest;
import com.reconciliation.engine.dto.reconciliation.ReconciliationResult;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Settlement;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.repository.AccountRepository;
import com.reconciliation.engine.repository.SettlementRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP -> controller -> service -> repository -> PostgreSQL, following
 * the same per-class Testcontainers pattern as {@code TransactionApiIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReconciliationApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private String reconciliationUrl(String path) {
        return "http://localhost:" + port + "/api/v1/reconciliations" + path;
    }

    private Account account() {
        String accNum = "ACC-RECON-" + System.nanoTime();
        return accountRepository.save(new Account(accNum, "CUST-RECON", "USD", AccountStatus.ACTIVE));
    }

    private Transaction transaction(Account account, String ref, BigDecimal amount, String currency, TransactionStatus status) {
        return transactionRepository.save(new Transaction(ref, account, amount, currency, TransactionType.PAYMENT,
                status, LocalDateTime.now(), null));
    }

    @Test
    void matchedTransactionReturnsMatched() {
        String ref = "TXN-IT-MATCH-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("50.0000"), "USD", TransactionStatus.COMPLETED);
        settlementRepository.save(new Settlement(ref, "EXT-" + ref, new BigDecimal("50.0000"), "USD",
                SettlementStatus.SETTLED, LocalDateTime.now()));

        ResponseEntity<ReconciliationResult> response = restTemplate.postForEntity(
                reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getStatus().name()).isEqualTo("MATCHED");
    }

    @Test
    void amountMismatchIsDetected() {
        String ref = "TXN-IT-AMT-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("50.0000"), "USD", TransactionStatus.COMPLETED);
        settlementRepository.save(new Settlement(ref, "EXT-" + ref, new BigDecimal("40.0000"), "USD",
                SettlementStatus.SETTLED, LocalDateTime.now()));

        ResponseEntity<ReconciliationResult> response = restTemplate.postForEntity(
                reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        assertThat(response.getBody().getStatus().name()).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    void currencyMismatchIsDetected() {
        String ref = "TXN-IT-CUR-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("50.0000"), "USD", TransactionStatus.COMPLETED);
        settlementRepository.save(new Settlement(ref, "EXT-" + ref, new BigDecimal("50.0000"), "EUR",
                SettlementStatus.SETTLED, LocalDateTime.now()));

        ResponseEntity<ReconciliationResult> response = restTemplate.postForEntity(
                reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        assertThat(response.getBody().getStatus().name()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void missingSettlementIsDetected() {
        String ref = "TXN-IT-MISS-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("50.0000"), "USD", TransactionStatus.PENDING);

        ResponseEntity<ReconciliationResult> response = restTemplate.postForEntity(
                reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        assertThat(response.getBody().getStatus().name()).isEqualTo("MISSING_SETTLEMENT");
    }

    @Test
    void unknownTransactionReturns404() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                reconciliationUrl(""), new ReconcileTransactionRequest("DOES-NOT-EXIST"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyReturnsPreviouslyPersistedRecordsNewestFirst() {
        String ref = "TXN-IT-HIST-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("50.0000"), "USD", TransactionStatus.PENDING);

        // First reconciliation: no settlement yet -> MISSING_SETTLEMENT
        restTemplate.postForEntity(reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        // Settlement arrives, matching amount/currency/status
        settlementRepository.save(new Settlement(ref, "EXT-" + ref, new BigDecimal("50.0000"), "USD",
                SettlementStatus.PENDING, LocalDateTime.now()));
        restTemplate.postForEntity(reconciliationUrl(""), new ReconcileTransactionRequest(ref), ReconciliationResult.class);

        ResponseEntity<ReconciliationResult[]> response = restTemplate.getForEntity(
                reconciliationUrl("/" + ref), ReconciliationResult[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ReconciliationResult> history = List.of(response.getBody());
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getStatus().name()).isEqualTo("MATCHED");
        assertThat(history.get(1).getStatus().name()).isEqualTo("MISSING_SETTLEMENT");
    }

    @Test
    void batchEndpointReconcilesAllTransactions() {
        String ref = "TXN-IT-BATCH-" + System.nanoTime();
        transaction(account(), ref, new BigDecimal("10.0000"), "USD", TransactionStatus.PENDING);

        ResponseEntity<BatchReconciliationResult> response = restTemplate.postForEntity(
                reconciliationUrl("/run"), null, BatchReconciliationResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalProcessed()).isGreaterThanOrEqualTo(1);
    }
}
