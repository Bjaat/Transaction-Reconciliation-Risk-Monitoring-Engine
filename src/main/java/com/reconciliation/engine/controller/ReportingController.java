package com.reconciliation.engine.controller;

import com.reconciliation.engine.dto.report.ReconciliationReportResponse;
import com.reconciliation.engine.dto.report.RiskReportResponse;
import com.reconciliation.engine.dto.report.TransactionReportResponse;
import com.reconciliation.engine.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionReportResponse> transactions(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(reportingService.transactions(utc(from), utc(to)));
    }

    @GetMapping("/reconciliations")
    public ResponseEntity<ReconciliationReportResponse> reconciliations(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(reportingService.reconciliations(utc(from), utc(to)));
    }

    @GetMapping("/risks")
    public ResponseEntity<RiskReportResponse> risks(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(reportingService.risks(utc(from), utc(to)));
    }

    /**
     * The schema stores timestamps without a zone. Normalize API values to
     * UTC before crossing the service boundary so their instant is retained
     * instead of silently discarding the supplied offset.
     */
    private LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC);
    }
}
