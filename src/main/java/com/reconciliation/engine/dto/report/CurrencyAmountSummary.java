package com.reconciliation.engine.dto.report;

import java.math.BigDecimal;

/** Monetary aggregation kept within a single currency. */
public record CurrencyAmountSummary(String currency, long transactionCount, BigDecimal totalAmount) {
}
