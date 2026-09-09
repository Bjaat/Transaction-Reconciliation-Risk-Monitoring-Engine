package com.reconciliation.engine.risk;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateTransactionRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void flagsAnExternalReferenceUsedByMultipleTransactions() {
        when(transactionRepository.countByExternalReference("EXT-1")).thenReturn(2L);
        DuplicateTransactionRule rule = new DuplicateTransactionRule(transactionRepository);

        assertThat(rule.evaluate(transaction("EXT-1"))).isPresent()
                .get().extracting(RiskFinding::ruleCode).isEqualTo(DuplicateTransactionRule.CODE);
    }

    @Test
    void ignoresMissingExternalReferencesWithoutQueryingTheRepository() {
        DuplicateTransactionRule rule = new DuplicateTransactionRule(transactionRepository);

        assertThat(rule.evaluate(transaction(null))).isEmpty();
    }

    private Transaction transaction(String externalReference) {
        Account account = new Account("ACC-1", "CUST-1", "USD", AccountStatus.ACTIVE);
        return new Transaction("TX-1", account, BigDecimal.TEN, "USD", TransactionType.PAYMENT,
                TransactionStatus.PENDING, LocalDateTime.now(), externalReference);
    }
}
