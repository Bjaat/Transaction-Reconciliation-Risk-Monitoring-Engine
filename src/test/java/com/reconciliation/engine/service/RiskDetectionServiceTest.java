package com.reconciliation.engine.service;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.RiskFlag;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.RiskFlagRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import com.reconciliation.engine.risk.RiskFinding;
import com.reconciliation.engine.risk.RiskRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskDetectionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private RiskFlagRepository riskFlagRepository;
    @Mock private RiskRule rule;

    private RiskDetectionService service;

    @BeforeEach
    void setUp() {
        service = new RiskDetectionService(transactionRepository, riskFlagRepository, List.of(rule));
    }

    @Test
    void persistsAFlagRaisedByARule() {
        Transaction transaction = transaction("TX-1");
        when(transactionRepository.findByTransactionReference("TX-1")).thenReturn(Optional.of(transaction));
        when(rule.evaluate(transaction)).thenReturn(Optional.of(
                new RiskFinding("TEST_RULE", RiskSeverity.HIGH, "test finding")));
        when(riskFlagRepository.existsByTransactionIdAndRuleCodeAndStatus(null, "TEST_RULE", RiskFlagStatus.OPEN))
                .thenReturn(false);
        when(riskFlagRepository.save(any(RiskFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.evaluate("TX-1");

        assertThat(result.getRulesEvaluated()).isEqualTo(1);
        assertThat(result.getFlagsCreated()).singleElement()
                .extracting(flag -> flag.getRuleCode()).isEqualTo("TEST_RULE");
    }

    @Test
    void doesNotCreateADuplicateOpenFlag() {
        Transaction transaction = transaction("TX-2");
        when(transactionRepository.findByTransactionReference("TX-2")).thenReturn(Optional.of(transaction));
        when(rule.evaluate(transaction)).thenReturn(Optional.of(
                new RiskFinding("TEST_RULE", RiskSeverity.HIGH, "test finding")));
        when(riskFlagRepository.existsByTransactionIdAndRuleCodeAndStatus(null, "TEST_RULE", RiskFlagStatus.OPEN))
                .thenReturn(true);

        assertThat(service.evaluate("TX-2").getFlagsCreated()).isEmpty();
        verify(riskFlagRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownTransaction() {
        when(transactionRepository.findByTransactionReference("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(rule, never()).evaluate(any());
    }

    private Transaction transaction(String reference) {
        Account account = new Account("ACC-1", "CUST-1", "USD", AccountStatus.ACTIVE);
        return new Transaction(reference, account, BigDecimal.TEN, "USD", TransactionType.PAYMENT,
                TransactionStatus.PENDING, LocalDateTime.now(), null);
    }
}
