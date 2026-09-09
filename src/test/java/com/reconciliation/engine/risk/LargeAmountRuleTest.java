package com.reconciliation.engine.risk;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LargeAmountRuleTest {

    private final LargeAmountRule rule = new LargeAmountRule(new BigDecimal("10000"));

    @Test
    void flagsAmountsAtTheConfiguredThreshold() {
        assertThat(rule.evaluate(transaction("10000.00"))).isPresent()
                .get().extracting(RiskFinding::ruleCode).isEqualTo(LargeAmountRule.CODE);
    }

    @Test
    void ignoresAmountsBelowTheConfiguredThreshold() {
        assertThat(rule.evaluate(transaction("9999.99"))).isEmpty();
    }

    private Transaction transaction(String amount) {
        Account account = new Account("ACC-1", "CUST-1", "USD", AccountStatus.ACTIVE);
        return new Transaction("TX-1", account, new BigDecimal(amount), "USD", TransactionType.PAYMENT,
                TransactionStatus.PENDING, LocalDateTime.now(), null);
    }
}
