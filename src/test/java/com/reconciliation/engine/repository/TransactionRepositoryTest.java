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
}
