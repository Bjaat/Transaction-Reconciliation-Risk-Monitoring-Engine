package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.RiskFlag;
import com.reconciliation.engine.entity.Transaction;
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
class RiskFlagRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RiskFlagRepository riskFlagRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Transaction persistedTransaction(String reference) {
        Account account = accountRepository.saveAndFlush(
                new Account("ACC-RF-" + reference, "CUST-001", "USD", AccountStatus.ACTIVE));
        return transactionRepository.saveAndFlush(new Transaction(
                reference, account, BigDecimal.TEN, "USD", TransactionType.PAYMENT,
                TransactionStatus.COMPLETED, LocalDateTime.now(), null));
    }

    private RiskFlag newFlag(Transaction transaction, String ruleCode, RiskFlagStatus status) {
        return new RiskFlag(transaction, ruleCode, RiskSeverity.HIGH, "Suspicious activity detected",
                LocalDateTime.now(), status);
    }

    @Test
    void savesAndReloadsARiskFlagLinkedToItsTransaction() {
        Transaction transaction = persistedTransaction("TXN-RF-1");

        RiskFlag saved = riskFlagRepository.saveAndFlush(
                newFlag(transaction, "LARGE_AMOUNT", RiskFlagStatus.OPEN));

        RiskFlag reloaded = riskFlagRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTransaction().getId()).isEqualTo(transaction.getId());
        assertThat(reloaded.getRuleCode()).isEqualTo("LARGE_AMOUNT");
        assertThat(reloaded.getSeverity()).isEqualTo(RiskSeverity.HIGH);
        assertThat(reloaded.getStatus()).isEqualTo(RiskFlagStatus.OPEN);
    }

    @Test
    void rejectsTwoConcurrentlyOpenFlagsForTheSameTransactionAndRule() {
        Transaction transaction = persistedTransaction("TXN-RF-2");
        riskFlagRepository.saveAndFlush(newFlag(transaction, "DUPLICATE_TRANSACTION", RiskFlagStatus.OPEN));

        assertThatThrownBy(() -> riskFlagRepository.saveAndFlush(
                newFlag(transaction, "DUPLICATE_TRANSACTION", RiskFlagStatus.OPEN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsARaisedFlagAfterAnEarlierOneForTheSameRuleWasResolved() {
        // Proves the index is genuinely partial (WHERE status = 'OPEN'), not
        // a plain unique constraint on (transaction_id, rule_code).
        Transaction transaction = persistedTransaction("TXN-RF-3");
        RiskFlag resolved = newFlag(transaction, "DUPLICATE_TRANSACTION", RiskFlagStatus.RESOLVED);
        riskFlagRepository.saveAndFlush(resolved);

        RiskFlag reopened = riskFlagRepository.saveAndFlush(
                newFlag(transaction, "DUPLICATE_TRANSACTION", RiskFlagStatus.OPEN));

        assertThat(reopened.getId()).isNotNull();
        assertThat(riskFlagRepository.findByTransactionId(transaction.getId())).hasSize(2);
    }

    @Test
    void findBySeverityFiltersCorrectly() {
        Transaction transaction = persistedTransaction("TXN-RF-4");
        riskFlagRepository.saveAndFlush(newFlag(transaction, "RULE_A", RiskFlagStatus.OPEN));
        RiskFlag critical = new RiskFlag(transaction, "RULE_B", RiskSeverity.CRITICAL, "Critical issue",
                LocalDateTime.now(), RiskFlagStatus.OPEN);
        riskFlagRepository.saveAndFlush(critical);

        var results = riskFlagRepository.findBySeverity(RiskSeverity.CRITICAL);

        assertThat(results).extracting(RiskFlag::getRuleCode).containsExactly("RULE_B");
    }
}
