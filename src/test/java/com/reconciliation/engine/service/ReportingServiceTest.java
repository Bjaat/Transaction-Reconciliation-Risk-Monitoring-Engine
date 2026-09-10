package com.reconciliation.engine.service;

import com.reconciliation.engine.repository.ReconciliationLogRepository;
import com.reconciliation.engine.repository.RiskFlagRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import com.reconciliation.engine.repository.projection.CurrencyAmountProjection;
import com.reconciliation.engine.repository.projection.DimensionCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private ReconciliationLogRepository reconciliationLogRepository;
    @Mock private RiskFlagRepository riskFlagRepository;
    @Mock private DimensionCountProjection firstCount;
    @Mock private DimensionCountProjection secondCount;
    @Mock private CurrencyAmountProjection currencyAmount;

    private ReportingService service;

    @BeforeEach
    void setUp() {
        service = new ReportingService(transactionRepository, reconciliationLogRepository, riskFlagRepository);
    }

    @Test
    void buildsTransactionReportWithoutCombiningCurrencies() {
        when(firstCount.getGroupValue()).thenReturn("COMPLETED");
        when(firstCount.getTotal()).thenReturn(2L);
        when(secondCount.getGroupValue()).thenReturn("PAYMENT");
        when(secondCount.getTotal()).thenReturn(2L);
        when(currencyAmount.getCurrency()).thenReturn("USD");
        when(currencyAmount.getTransactionCount()).thenReturn(2L);
        when(currencyAmount.getTotalAmount()).thenReturn(new BigDecimal("30.0000"));
        when(transactionRepository.summarizeStatus(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(firstCount));
        when(transactionRepository.summarizeType(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(secondCount));
        when(transactionRepository.summarizeAmountsByCurrency(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(currencyAmount));

        var report = service.transactions(null, null);

        assertThat(report.totalTransactions()).isEqualTo(2);
        assertThat(report.amountsByCurrency()).singleElement().satisfies(amount -> {
            assertThat(amount.currency()).isEqualTo("USD");
            assertThat(amount.totalAmount()).isEqualByComparingTo("30.0000");
        });
        assertThat(report.countsByStatus()).containsExactly(new com.reconciliation.engine.dto.report.DimensionCount("COMPLETED", 2));
    }

    @Test
    void returnsZeroAndEmptyGroupsForEmptyRiskData() {
        when(riskFlagRepository.summarizeStatus(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(riskFlagRepository.summarizeSeverity(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(riskFlagRepository.summarizeRule(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var report = service.risks(null, null);

        assertThat(report.totalFlags()).isZero();
        assertThat(report.countsByStatus()).isEmpty();
        assertThat(report.countsBySeverity()).isEmpty();
        assertThat(report.countsByRule()).isEmpty();
    }

    @Test
    void totalsReconciliationGroups() {
        when(firstCount.getGroupValue()).thenReturn("MATCHED");
        when(firstCount.getTotal()).thenReturn(4L);
        when(secondCount.getGroupValue()).thenReturn("AMOUNT_MISMATCH");
        when(secondCount.getTotal()).thenReturn(1L);
        when(reconciliationLogRepository.summarizeResults(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(firstCount, secondCount));

        var report = service.reconciliations(null, null);

        assertThat(report.totalReconciliations()).isEqualTo(5);
        assertThat(report.countsByResult()).hasSize(2);
    }

    @Test
    void rejectsReversedTimeWindowBeforeQueryingRepositories() {
        LocalDateTime from = LocalDateTime.parse("2026-09-02T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-09-01T00:00:00");

        assertThatThrownBy(() -> service.transactions(from, to))
                .isInstanceOf(com.reconciliation.engine.exception.BadRequestException.class)
                .hasMessageContaining("from");
    }
}
