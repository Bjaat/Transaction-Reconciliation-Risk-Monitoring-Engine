package com.reconciliation.engine.risk;

import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DuplicateTransactionRule implements RiskRule {

    public static final String CODE = "DUPLICATE_TRANSACTION";

    private final TransactionRepository transactionRepository;

    public DuplicateTransactionRule(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskFinding> evaluate(Transaction transaction) {
        String externalReference = transaction.getExternalReference();
        if (externalReference == null || externalReference.isBlank()
                || transactionRepository.countByExternalReference(externalReference) < 2) {
            return Optional.empty();
        }
        return Optional.of(new RiskFinding(CODE, RiskSeverity.CRITICAL,
                "External reference " + externalReference + " is shared by multiple transactions"));
    }
}
