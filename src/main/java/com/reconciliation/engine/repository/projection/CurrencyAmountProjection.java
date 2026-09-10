package com.reconciliation.engine.repository.projection;

import java.math.BigDecimal;

/** Transaction count and monetary total for one currency. */
public interface CurrencyAmountProjection {

    String getCurrency();

    long getTransactionCount();

    BigDecimal getTotalAmount();
}
