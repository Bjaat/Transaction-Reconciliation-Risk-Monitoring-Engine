package com.reconciliation.engine.service;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.common.SettlementStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.reconciliation.ReconciliationResult;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.ReconciliationLog;
import com.reconciliation.engine.entity.Settlement;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.ReconciliationLogRepository;
import com.reconciliation.engine.repository.SettlementRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private ReconciliationLogRepository reconciliationLogRepository;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(transactionRepository, settlementRepository, reconciliationLogRepository);
    }

    private Transaction transaction(String ref, BigDecimal amount, String currency, TransactionStatus status) {
        Account account = new Account("ACC-1", "CUST-1", currency, AccountStatus.ACTIVE);
        return new Transaction(ref, account, amount, currency, TransactionType.PAYMENT, status, LocalDateTime.now(), null);
    }

    private Settlement settlement(String txRef, String extRef, BigDecimal amount, String currency, SettlementStatus status) {
        return new Settlement(txRef, extRef, amount, currency, status, LocalDateTime.now());
    }

    @Test
    void matchedWhenAmountCurrencyAndStatusAllAgree() {
        Transaction tx = transaction("TXN-1", new BigDecimal("100.00"), "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-1")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-1")).thenReturn(
                List.of(settlement("TXN-1", "EXT-1", new BigDecimal("100.00"), "USD", SettlementStatus.SETTLED)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-1");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    void amountMismatchWhenAmountsDiffer() {
        Transaction tx = transaction("TXN-2", new BigDecimal("100.00"), "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-2")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-2")).thenReturn(
                List.of(settlement("TXN-2", "EXT-2", new BigDecimal("90.00"), "USD", SettlementStatus.SETTLED)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-2");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
        assertThat(result.getActualAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void currencyMismatchWhenCurrenciesDiffer() {
        Transaction tx = transaction("TXN-3", new BigDecimal("100.00"), "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-3")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-3")).thenReturn(
                List.of(settlement("TXN-3", "EXT-3", new BigDecimal("100.00"), "EUR", SettlementStatus.SETTLED)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-3");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.CURRENCY_MISMATCH);
        assertThat(result.getExpectedCurrency()).isEqualTo("USD");
        assertThat(result.getActualCurrency()).isEqualTo("EUR");
    }

    @Test
    void missingSettlementWhenNoneFound() {
        Transaction tx = transaction("TXN-4", BigDecimal.TEN, "USD", TransactionStatus.PENDING);
        when(transactionRepository.findByTransactionReference("TXN-4")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-4")).thenReturn(List.of());
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-4");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.MISSING_SETTLEMENT);
        assertThat(result.getSettlementReference()).isNull();
    }

    @Test
    void duplicateSettlementWhenMoreThanOneFound() {
        Transaction tx = transaction("TXN-5", BigDecimal.TEN, "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-5")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-5")).thenReturn(List.of(
                settlement("TXN-5", "EXT-A", BigDecimal.TEN, "USD", SettlementStatus.SETTLED),
                settlement("TXN-5", "EXT-B", BigDecimal.TEN, "USD", SettlementStatus.SETTLED)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-5");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.DUPLICATE_SETTLEMENT);
    }

    @Test
    void statusMismatchWhenAmountAndCurrencyAgreeButStatusDoesNot() {
        Transaction tx = transaction("TXN-6", BigDecimal.TEN, "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-6")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-6")).thenReturn(
                List.of(settlement("TXN-6", "EXT-6", BigDecimal.TEN, "USD", SettlementStatus.PENDING)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationResult result = service.reconcile("TXN-6");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.STATUS_MISMATCH);
    }

    @Test
    void reconcileThrowsNotFoundForUnknownTransaction() {
        when(transactionRepository.findByTransactionReference("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconcile("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reconciliationLogRepository, never()).save(any());
    }

    @Test
    void reconcilePersistsTheLog() {
        Transaction tx = transaction("TXN-7", BigDecimal.TEN, "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-7")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-7")).thenReturn(
                List.of(settlement("TXN-7", "EXT-7", BigDecimal.TEN, "USD", SettlementStatus.SETTLED)));
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reconcile("TXN-7");

        ArgumentCaptor<ReconciliationLog> captor = ArgumentCaptor.forClass(ReconciliationLog.class);
        verify(reconciliationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(captor.getValue().getTransactionReference()).isEqualTo("TXN-7");
    }

    @Test
    void repeatReconciliationCreatesANewLogEachTime() {
        Transaction tx = transaction("TXN-8", BigDecimal.TEN, "USD", TransactionStatus.PENDING);
        when(transactionRepository.findByTransactionReference("TXN-8")).thenReturn(Optional.of(tx));
        when(settlementRepository.findByTransactionReference("TXN-8")).thenReturn(List.of());
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reconcile("TXN-8");
        service.reconcile("TXN-8");

        verify(reconciliationLogRepository, times(2)).save(any(ReconciliationLog.class));
    }

    @Test
    void getHistoryThrowsNotFoundForUnknownTransaction() {
        when(transactionRepository.findByTransactionReference("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHistoryReturnsNewestFirst() {
        Transaction tx = transaction("TXN-9", BigDecimal.TEN, "USD", TransactionStatus.COMPLETED);
        when(transactionRepository.findByTransactionReference("TXN-9")).thenReturn(Optional.of(tx));
        ReconciliationLog older = new ReconciliationLog("TXN-9", null, ReconciliationStatus.MISSING_SETTLEMENT,
                BigDecimal.TEN, null, null, "COMPLETED", null, "first attempt", LocalDateTime.now().minusHours(1));
        ReconciliationLog newer = new ReconciliationLog("TXN-9", "EXT-9", ReconciliationStatus.MATCHED,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, "COMPLETED", "SETTLED", "second attempt", LocalDateTime.now());
        when(reconciliationLogRepository.findByTransactionReferenceOrderByReconciledAtDesc("TXN-9"))
                .thenReturn(List.of(newer, older));

        List<ReconciliationResult> history = service.getHistory("TXN-9");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(history.get(1).getStatus()).isEqualTo(ReconciliationStatus.MISSING_SETTLEMENT);
    }

    @Test
    void reconcileAllTalliesEachOutcomeAndSkipsFailures() {
        Transaction matchedTx = transaction("TXN-A", BigDecimal.TEN, "USD", TransactionStatus.COMPLETED);
        Transaction missingTx = transaction("TXN-B", BigDecimal.TEN, "USD", TransactionStatus.PENDING);
        when(transactionRepository.findAll()).thenReturn(List.of(matchedTx, missingTx));
        when(settlementRepository.findByTransactionReference("TXN-A")).thenReturn(
                List.of(settlement("TXN-A", "EXT-A", BigDecimal.TEN, "USD", SettlementStatus.SETTLED)));
        when(settlementRepository.findByTransactionReference("TXN-B")).thenReturn(List.of());
        when(reconciliationLogRepository.save(any(ReconciliationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        var batch = service.reconcileAll();

        assertThat(batch.getTotalProcessed()).isEqualTo(2);
        assertThat(batch.getMatched()).isEqualTo(1);
        assertThat(batch.getMissingSettlement()).isEqualTo(1);
        assertThat(batch.getFailed()).isEqualTo(0);
    }
}
