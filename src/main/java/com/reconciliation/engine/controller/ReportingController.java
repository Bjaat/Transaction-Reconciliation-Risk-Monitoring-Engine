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
        return ResponseEntity.ok(reportingService.transactions(local(from), local(to)));
    }

    @GetMapping("/reconciliations")
    public ResponseEntity<ReconciliationReportResponse> reconciliations(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(reportingService.reconciliations(local(from), local(to)));
    }

    @GetMapping("/risks")
    public ResponseEntity<RiskReportResponse> risks(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        return ResponseEntity.ok(reportingService.risks(local(from), local(to)));
    }

    private LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
