package com.reconciliation.engine.risk;

import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.entity.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class LargeAmountRule implements RiskRule {

    public static final String CODE = "LARGE_AMOUNT";

    private final BigDecimal threshold;

    public LargeAmountRule(@Value("${risk.rules.large-amount-threshold:10000}") BigDecimal threshold) {
        this.threshold = threshold;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Optional<RiskFinding> evaluate(Transaction transaction) {
        if (transaction.getAmount().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RiskFinding(CODE, RiskSeverity.HIGH,
                "Transaction amount " + transaction.getAmount().toPlainString()
                        + " meets or exceeds configured threshold " + threshold.toPlainString()));
    }
}
