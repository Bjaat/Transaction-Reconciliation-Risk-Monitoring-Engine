package com.reconciliation.engine.service;

import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.common.SettlementStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.dto.reconciliation.BatchReconciliationResult;
import com.reconciliation.engine.dto.reconciliation.ReconciliationResult;
import com.reconciliation.engine.entity.ReconciliationLog;
import com.reconciliation.engine.entity.Settlement;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.ReconciliationLogRepository;
import com.reconciliation.engine.repository.SettlementRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Compares transactions against settlement data and persists the outcome as
 * a {@link ReconciliationLog}.
 *
 * <b>Matching rule precedence</b> (documented assumption — the project spec
 * didn't pin this down, so it's made explicit here rather than left
 * implicit): a transaction is looked up by {@code transactionReference};
 * its settlements (by the same reference) are then classified, in order:
 * zero settlements -> MISSING_SETTLEMENT; more than one -> DUPLICATE_SETTLEMENT;
 * exactly one -> compare currency first (an amount comparison across
 * different currencies isn't meaningful) -> CURRENCY_MISMATCH; then amount
 * -> AMOUNT_MISMATCH; then status -> STATUS_MISMATCH; otherwise MATCHED.
 *
 * <b>Idempotency</b> (documented assumption): reconciling the same
 * transaction twice creates a NEW {@code ReconciliationLog} row each time
 * rather than overwriting the previous one. This matches how the entity was
 * already designed in Phase 2 — {@code CreationAudit} only (no
 * {@code updatedAt}), i.e. an append-only event log — and means "run
 * reconciliation again after the settlement feed catches up" naturally
 * produces a full history rather than losing the earlier MISSING_SETTLEMENT
 * result.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final SettlementRepository settlementRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;

    public ReconciliationService(TransactionRepository transactionRepository,
                                  SettlementRepository settlementRepository,
                                  ReconciliationLogRepository reconciliationLogRepository) {
        this.transactionRepository = transactionRepository;
        this.settlementRepository = settlementRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
    }

    @Transactional
    public ReconciliationResult reconcile(String transactionReference) {
        Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionReference));
        return reconcileTransaction(transaction);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationResult> getHistory(String transactionReference) {
        if (transactionRepository.findByTransactionReference(transactionReference).isEmpty()) {
            throw new ResourceNotFoundException("Transaction not found: " + transactionReference);
        }
        return reconciliationLogRepository.findByTransactionReferenceOrderByReconciledAtDesc(transactionReference)
                .stream().map(ReconciliationResult::fromEntity).toList();
    }

    /**
     * Reconciles every transaction in the system. Deliberately NOT annotated
     * {@code @Transactional} at this method level: each call to
     * {@link #reconcile(String)} below is a self-invocation within the same
     * bean, which bypasses Spring's proxy-based {@code @Transactional} — so
     * wrapping this method in one big transaction would not actually give
     * true all-or-nothing semantics anyway, and each underlying repository
     * {@code save()} already commits independently. That happens to be
     * exactly the desired behavior here (spec: one bad transaction must not
     * block unrelated ones), so it's left as-is rather than engineered
     * around with {@code REQUIRES_NEW} propagation for a batch job at this
     * scale.
     */
    public BatchReconciliationResult reconcileAll() {
        List<Transaction> transactions = transactionRepository.findAll();

        int matched = 0, amountMismatch = 0, currencyMismatch = 0, statusMismatch = 0,
                missingSettlement = 0, duplicateSettlement = 0, failed = 0;

        for (Transaction transaction : transactions) {
            try {
                ReconciliationResult result = reconcileTransaction(transaction);
                switch (result.getStatus()) {
                    case MATCHED -> matched++;
                    case AMOUNT_MISMATCH -> amountMismatch++;
                    case CURRENCY_MISMATCH -> currencyMismatch++;
                    case STATUS_MISMATCH -> statusMismatch++;
                    case MISSING_SETTLEMENT -> missingSettlement++;
                    case DUPLICATE_SETTLEMENT -> duplicateSettlement++;
                    case UNMATCHED_SETTLEMENT -> { /* not produced by per-transaction reconciliation */ }
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Reconciliation failed for transaction {}: {}",
                        transaction.getTransactionReference(), ex.getMessage());
            }
        }

        return new BatchReconciliationResult(transactions.size(), matched, amountMismatch, currencyMismatch,
                statusMismatch, missingSettlement, duplicateSettlement, failed);
    }

    private ReconciliationResult reconcileTransaction(Transaction transaction) {
        List<Settlement> settlements = settlementRepository.findByTransactionReference(
                transaction.getTransactionReference());
        ReconciliationLog logEntry = evaluate(transaction, settlements);
        ReconciliationLog saved = reconciliationLogRepository.save(logEntry);
        return ReconciliationResult.fromEntity(saved);
    }

    private ReconciliationLog evaluate(Transaction transaction, List<Settlement> settlements) {
        LocalDateTime now = LocalDateTime.now();
        String txRef = transaction.getTransactionReference();

        if (settlements.isEmpty()) {
            return new ReconciliationLog(txRef, null, ReconciliationStatus.MISSING_SETTLEMENT,
                    transaction.getAmount(), null, null,
                    transaction.getStatus().name(), null,
                    "No settlement found for transaction reference " + txRef,
                    now, transaction.getCurrency(), null);
        }

        if (settlements.size() > 1) {
            return new ReconciliationLog(txRef, null, ReconciliationStatus.DUPLICATE_SETTLEMENT,
                    transaction.getAmount(), null, null,
                    transaction.getStatus().name(), null,
                    settlements.size() + " settlement records found for transaction reference " + txRef
                            + " — cannot determine which one to reconcile against",
                    now, transaction.getCurrency(), null);
        }

        Settlement settlement = settlements.get(0);

        if (!transaction.getCurrency().equals(settlement.getCurrency())) {
            return new ReconciliationLog(txRef, settlement.getExternalSettlementReference(),
                    ReconciliationStatus.CURRENCY_MISMATCH,
                    transaction.getAmount(), settlement.getAmount(), null,
                    transaction.getStatus().name(), settlement.getStatus().name(),
                    "Currency mismatch: transaction is " + transaction.getCurrency()
                            + " but settlement reported " + settlement.getCurrency(),
                    now, transaction.getCurrency(), settlement.getCurrency());
        }

        BigDecimal difference = transaction.getAmount().subtract(settlement.getAmount());
        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            return new ReconciliationLog(txRef, settlement.getExternalSettlementReference(),
                    ReconciliationStatus.AMOUNT_MISMATCH,
                    transaction.getAmount(), settlement.getAmount(), difference,
                    transaction.getStatus().name(), settlement.getStatus().name(),
                    "Amount mismatch: transaction is " + transaction.getAmount()
                            + " but settlement reported " + settlement.getAmount(),
                    now, transaction.getCurrency(), settlement.getCurrency());
        }

        if (!statusesAgree(transaction.getStatus(), settlement.getStatus())) {
            return new ReconciliationLog(txRef, settlement.getExternalSettlementReference(),
                    ReconciliationStatus.STATUS_MISMATCH,
                    transaction.getAmount(), settlement.getAmount(), BigDecimal.ZERO,
                    transaction.getStatus().name(), settlement.getStatus().name(),
                    "Status mismatch: transaction is " + transaction.getStatus()
                            + " but settlement is " + settlement.getStatus(),
                    now, transaction.getCurrency(), settlement.getCurrency());
        }

        return new ReconciliationLog(txRef, settlement.getExternalSettlementReference(),
                ReconciliationStatus.MATCHED,
                transaction.getAmount(), settlement.getAmount(), BigDecimal.ZERO,
                transaction.getStatus().name(), settlement.getStatus().name(),
                "Transaction and settlement match on amount, currency, and status",
                now, transaction.getCurrency(), settlement.getCurrency());
    }

    /**
     * {@code TransactionStatus} and {@code SettlementStatus} are separate
     * enums with non-overlapping value sets (by design — see their
     * Javadoc), so "agreement" has to be an explicit semantic mapping
     * rather than an equality check. Documented assumption: COMPLETED
     * transactions expect a SETTLED settlement; FAILED/REVERSED expect the
     * same-named settlement status; PENDING and PROCESSING (no settlement
     * equivalent for "processing") both expect a PENDING settlement.
     */
    private boolean statusesAgree(TransactionStatus transactionStatus, SettlementStatus settlementStatus) {
        return switch (transactionStatus) {
            case COMPLETED -> settlementStatus == SettlementStatus.SETTLED;
            case FAILED -> settlementStatus == SettlementStatus.FAILED;
            case REVERSED -> settlementStatus == SettlementStatus.REVERSED;
            case PENDING, PROCESSING -> settlementStatus == SettlementStatus.PENDING;
        };
    }
}
