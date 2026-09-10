package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account persistedAccount(String accountNumber) {
        return accountRepository.saveAndFlush(new Account(accountNumber, "CUST-001", "USD", AccountStatus.ACTIVE));
    }

    private Transaction newTransaction(String reference, Account account, BigDecimal amount, TransactionStatus status) {
        return new Transaction(reference, account, amount, "USD", TransactionType.PAYMENT, status,
                LocalDateTime.now(), "EXT-REF-1");
    }

    @Test
    void savesAndReloadsATransactionLinkedToItsAccount() {
        Account account = persistedAccount("ACC-2001");
        Transaction saved = transactionRepository.saveAndFlush(
                newTransaction("TXN-1001", account, new BigDecimal("125.5000"), TransactionStatus.COMPLETED));

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getTransactionReference()).isEqualTo("TXN-1001");
        assertThat(reloaded.getAccount().getId()).isEqualTo(account.getId());
        assertThat(reloaded.getAccount().getAccountNumber()).isEqualTo("ACC-2001");
        assertThat(reloaded.getTransactionType()).isEqualTo(TransactionType.PAYMENT);
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    void preservesBigDecimalScaleToFourDecimalPlaces() {
        Account account = persistedAccount("ACC-2002");
        // Deliberately sub-cent precision to prove NUMERIC(19,4) round-trips
        // exactly — this would silently lose precision with a double.
        BigDecimal preciseAmount = new BigDecimal("99.1234");

        Transaction saved = transactionRepository.saveAndFlush(
                newTransaction("TXN-1002", account, preciseAmount, TransactionStatus.PENDING));
        transactionRepository.flush();

        Transaction reloaded = transactionRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualByComparingTo(preciseAmount);
    }

    @Test
    void rejectsDuplicateTransactionReferences() {
        Account account = persistedAccount("ACC-2003");
        transactionRepository.saveAndFlush(
                newTransaction("TXN-DUP", account, BigDecimal.TEN, TransactionStatus.PENDING));

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(
                newTransaction("TXN-DUP", account, BigDecimal.ONE, TransactionStatus.PENDING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByAccountIdReturnsOnlyThatAccountsTransactions() {
        Account accountA = persistedAccount("ACC-2004");
        Account accountB = persistedAccount("ACC-2005");
        transactionRepository.saveAndFlush(newTransaction("TXN-A1", accountA, BigDecimal.TEN, TransactionStatus.COMPLETED));
        transactionRepository.saveAndFlush(newTransaction("TXN-A2", accountA, BigDecimal.TEN, TransactionStatus.COMPLETED));
        transactionRepository.saveAndFlush(newTransaction("TXN-B1", accountB, BigDecimal.TEN, TransactionStatus.COMPLETED));

        var page = transactionRepository.findByAccountId(accountA.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2)
                .extracting(Transaction::getTransactionReference)
                .containsExactlyInAnyOrder("TXN-A1", "TXN-A2");
    }

    @Test
    void findByStatusFiltersCorrectly() {
        Account account = persistedAccount("ACC-2006");
        transactionRepository.saveAndFlush(newTransaction("TXN-F1", account, BigDecimal.TEN, TransactionStatus.FAILED));
        transactionRepository.saveAndFlush(newTransaction("TXN-F2", account, BigDecimal.TEN, TransactionStatus.COMPLETED));

        var page = transactionRepository.findByStatus(TransactionStatus.FAILED, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Transaction::getTransactionReference)
                .containsExactly("TXN-F1");
    }

    @Test
    void countsTransactionsSharingAnExternalReference() {
        Account account = persistedAccount("ACC-2007");
        transactionRepository.saveAndFlush(newTransaction("TXN-E1", account, BigDecimal.TEN, TransactionStatus.PENDING));
        transactionRepository.saveAndFlush(newTransaction("TXN-E2", account, BigDecimal.ONE, TransactionStatus.PENDING));

        assertThat(transactionRepository.countByExternalReference("EXT-REF-1")).isEqualTo(2);
    }

    @Test
    void reportingQueriesAggregateAnInclusiveWindowAndKeepCurrenciesSeparate() {
        Account account = persistedAccount("ACC-REPORT");
        LocalDateTime from = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-09-02T00:00:00");
        transactionRepository.saveAndFlush(new Transaction("TXN-RPT-1", account, new BigDecimal("100.0000"),
                "USD", TransactionType.PAYMENT, TransactionStatus.COMPLETED, from, null));
        transactionRepository.saveAndFlush(new Transaction("TXN-RPT-2", account, new BigDecimal("200.0000"),
                "USD", TransactionType.PAYMENT, TransactionStatus.COMPLETED, to, null));
        transactionRepository.saveAndFlush(new Transaction("TXN-RPT-3", account, new BigDecimal("100.0000"),
                "EUR", TransactionType.TRANSFER, TransactionStatus.PROCESSING, from.plusHours(1), null));
        transactionRepository.saveAndFlush(new Transaction("TXN-RPT-4", account, new BigDecimal("200.0000"),
                "EUR", TransactionType.TRANSFER, TransactionStatus.PROCESSING, to.minusHours(1), null));
        transactionRepository.saveAndFlush(new Transaction("TXN-RPT-5", account, new BigDecimal("999.0000"),
                "GBP", TransactionType.REFUND, TransactionStatus.FAILED, to.plusSeconds(1), null));

        var statuses = transactionRepository.summarizeStatus(from, to);
        var types = transactionRepository.summarizeType(from, to);
        var amounts = transactionRepository.summarizeAmountsByCurrency(from, to);

        assertThat(statuses).extracting(row -> row.getGroupValue().toString(), row -> row.getTotal())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("COMPLETED", 2L),
                        org.assertj.core.groups.Tuple.tuple("PROCESSING", 2L));
        assertThat(types).extracting(row -> row.getGroupValue().toString(), row -> row.getTotal())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("PAYMENT", 2L),
                        org.assertj.core.groups.Tuple.tuple("TRANSFER", 2L));
        assertThat(amounts).hasSize(2);
        assertThat(amounts).filteredOn(row -> row.getCurrency().equals("USD")).singleElement().satisfies(row -> {
            assertThat(row.getTransactionCount()).isEqualTo(2);
            assertThat(row.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.0000"));
        });
        assertThat(amounts).filteredOn(row -> row.getCurrency().equals("EUR")).singleElement().satisfies(row -> {
            assertThat(row.getTransactionCount()).isEqualTo(2);
            assertThat(row.getTotalAmount()).isEqualByComparingTo(new BigDecimal("300.0000"));
        });
    }
}
