package com.reconciliation.engine.controller;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.transaction.CreateTransactionRequest;
import com.reconciliation.engine.dto.transaction.TransactionResponse;
import com.reconciliation.engine.dto.transaction.UpdateTransactionRequest;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the real flow: HTTP -> controller -> service ->
 * repository -> PostgreSQL (via Testcontainers, same pattern established in
 * Phase 2's repository tests — see those classes for why each test owns its
 * own container rather than sharing one).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/transactions";
        if (accountRepository.findByAccountNumber("ACC-IT-1").isEmpty()) {
            accountRepository.save(new Account("ACC-IT-1", "CUST-IT-1", "USD", AccountStatus.ACTIVE));
        }
    }

    @Test
    void createThenGetReturnsThePersistedTransaction() {
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                "TXN-IT-" + System.nanoTime(), "ACC-IT-1", new BigDecimal("250.7500"), "USD",
                TransactionType.DEPOSIT, LocalDateTime.now(), "EXT-IT-1");

        ResponseEntity<TransactionResponse> createResponse =
                restTemplate.postForEntity(baseUrl, createRequest, TransactionResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(created.getAmount()).isEqualByComparingTo("250.7500");

        ResponseEntity<TransactionResponse> getResponse =
                restTemplate.getForEntity(baseUrl + "/" + created.getId(), TransactionResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse fetched = getResponse.getBody();
        assertThat(fetched).isNotNull();
        assertThat(fetched.getTransactionReference()).isEqualTo(created.getTransactionReference());
        assertThat(fetched.getAccountNumber()).isEqualTo("ACC-IT-1");
        assertThat(fetched.getAmount()).isEqualByComparingTo("250.7500");
    }

    @Test
    void getMissingTransactionReturns404WithConsistentErrorBody() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/999999999", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"status\":404").contains("Transaction not found");
    }

    @Test
    void createWithUnknownAccountReturns404() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-IT-BAD-ACC", "DOES-NOT-EXIST", BigDecimal.TEN, "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), null);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createWithInvalidAmountReturns400() {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-IT-BAD-AMOUNT", "ACC-IT-1", new BigDecimal("-10.00"), "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), null);

        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateThenGetReflectsTheNewStatus() {
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                "TXN-IT-UPD-" + System.nanoTime(), "ACC-IT-1", BigDecimal.TEN, "USD",
                TransactionType.WITHDRAWAL, LocalDateTime.now(), null);
        Long id = restTemplate.postForEntity(baseUrl, createRequest, TransactionResponse.class)
                .getBody().getId();

        UpdateTransactionRequest updateRequest = new UpdateTransactionRequest(TransactionStatus.COMPLETED, "SETTLED-REF");
        restTemplate.put(baseUrl + "/" + id, updateRequest);

        ResponseEntity<TransactionResponse> getResponse =
                restTemplate.getForEntity(baseUrl + "/" + id, TransactionResponse.class);

        assertThat(getResponse.getBody().getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(getResponse.getBody().getExternalReference()).isEqualTo("SETTLED-REF");
    }

    @Test
    void listFiltersByAccountNumber() {
        String reference = "TXN-IT-FILTER-" + System.nanoTime();
        CreateTransactionRequest createRequest = new CreateTransactionRequest(
                reference, "ACC-IT-1", BigDecimal.TEN, "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), null);
        restTemplate.postForEntity(baseUrl, createRequest, TransactionResponse.class);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "?accountId=ACC-IT-1&page=0&size=50", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(reference);
    }
}
