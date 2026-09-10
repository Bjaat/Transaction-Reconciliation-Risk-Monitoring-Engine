package com.reconciliation.engine.dto.report;

import java.time.LocalDateTime;
import java.util.List;

public record RiskReportResponse(
        LocalDateTime from,
        LocalDateTime to,
        long totalFlags,
        List<DimensionCount> countsByStatus,
        List<DimensionCount> countsBySeverity,
        List<DimensionCount> countsByRule) {

    public RiskReportResponse {
        countsByStatus = List.copyOf(countsByStatus);
        countsBySeverity = List.copyOf(countsBySeverity);
        countsByRule = List.copyOf(countsByRule);
    }
}
