package com.reconciliation.engine.service;

import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.dto.risk.BatchRiskEvaluationResult;
import com.reconciliation.engine.dto.risk.RiskEvaluationResult;
import com.reconciliation.engine.dto.risk.RiskFlagResponse;
import com.reconciliation.engine.entity.RiskFlag;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.RiskFlagRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import com.reconciliation.engine.risk.RiskFinding;
import com.reconciliation.engine.risk.RiskRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RiskDetectionService.class);

    private final TransactionRepository transactionRepository;
    private final RiskFlagRepository riskFlagRepository;
    private final List<RiskRule> rules;

    public RiskDetectionService(TransactionRepository transactionRepository,
                                RiskFlagRepository riskFlagRepository,
                                List<RiskRule> rules) {
        this.transactionRepository = transactionRepository;
        this.riskFlagRepository = riskFlagRepository;
        this.rules = List.copyOf(rules);
    }

    @Transactional
    public RiskEvaluationResult evaluate(String transactionReference) {
        Transaction transaction = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionReference));
        return evaluate(transaction);
    }

    private RiskEvaluationResult evaluate(Transaction transaction) {
        List<RiskFlagResponse> created = new ArrayList<>();
        for (RiskRule rule : rules) {
            rule.evaluate(transaction).ifPresent(finding -> createFlagIfAbsent(transaction, finding, created));
        }
        return new RiskEvaluationResult(transaction.getTransactionReference(), rules.size(), created);
    }

    private void createFlagIfAbsent(Transaction transaction, RiskFinding finding, List<RiskFlagResponse> created) {
        if (riskFlagRepository.existsByTransactionIdAndRuleCodeAndStatus(
                transaction.getId(), finding.ruleCode(), RiskFlagStatus.OPEN)) {
            return;
        }
        RiskFlag saved = riskFlagRepository.save(new RiskFlag(transaction, finding.ruleCode(), finding.severity(),
                finding.description(), LocalDateTime.now(), RiskFlagStatus.OPEN));
        created.add(RiskFlagResponse.fromEntity(saved));
    }

    @Transactional(readOnly = true)
    public List<RiskFlagResponse> getFlags(String transactionReference) {
        if (!transactionRepository.existsByTransactionReference(transactionReference)) {
            throw new ResourceNotFoundException("Transaction not found: " + transactionReference);
        }
        return riskFlagRepository.findByTransactionTransactionReferenceOrderByDetectedAtDesc(transactionReference)
                .stream().map(RiskFlagResponse::fromEntity).toList();
    }

    @Transactional
    public BatchRiskEvaluationResult evaluateAll() {
        List<Transaction> transactions = transactionRepository.findAll();
        int flagsCreated = 0;
        int failed = 0;
        for (Transaction transaction : transactions) {
            try {
                flagsCreated += evaluate(transaction).getFlagsCreated().size();
            } catch (RuntimeException exception) {
                failed++;
                log.error("Risk evaluation failed for transaction {}", transaction.getTransactionReference(), exception);
            }
        }
        return new BatchRiskEvaluationResult(transactions.size(), flagsCreated, failed);
    }
}
