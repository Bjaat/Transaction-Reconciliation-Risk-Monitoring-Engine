package com.reconciliation.engine.dto.report;

import java.time.LocalDateTime;
import java.util.List;

public record ReconciliationReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        long totalReconciliations,
        List<DimensionCount> countsByResult) {

    public ReconciliationReportResponse {
        countsByResult = List.copyOf(countsByResult);
    }
}
