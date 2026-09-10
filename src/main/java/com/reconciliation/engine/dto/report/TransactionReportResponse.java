package com.reconciliation.engine.dto.report;

import java.time.LocalDateTime;
import java.util.List;

public record TransactionReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        long totalTransactions,
        List<CurrencyAmountSummary> amountsByCurrency,
        List<DimensionCount> countsByStatus,
        List<DimensionCount> countsByType) {

    public TransactionReportResponse {
        amountsByCurrency = List.copyOf(amountsByCurrency);
        countsByStatus = List.copyOf(countsByStatus);
        countsByType = List.copyOf(countsByType);
    }
}
