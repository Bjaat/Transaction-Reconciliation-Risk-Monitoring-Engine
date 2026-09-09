package com.reconciliation.engine.controller;

import com.reconciliation.engine.dto.risk.BatchRiskEvaluationResult;
import com.reconciliation.engine.dto.risk.EvaluateRiskRequest;
import com.reconciliation.engine.dto.risk.RiskEvaluationResult;
import com.reconciliation.engine.dto.risk.RiskFlagResponse;
import com.reconciliation.engine.service.RiskDetectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk")
public class RiskController {

    private final RiskDetectionService riskDetectionService;

    public RiskController(RiskDetectionService riskDetectionService) {
        this.riskDetectionService = riskDetectionService;
    }

    @PostMapping("/evaluations")
    public ResponseEntity<RiskEvaluationResult> evaluate(@Valid @RequestBody EvaluateRiskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(riskDetectionService.evaluate(request.getTransactionReference()));
    }

    @PostMapping("/evaluations/run")
    public ResponseEntity<BatchRiskEvaluationResult> evaluateAll() {
        return ResponseEntity.ok(riskDetectionService.evaluateAll());
    }

    @GetMapping("/flags/{transactionReference}")
    public ResponseEntity<List<RiskFlagResponse>> getFlags(@PathVariable String transactionReference) {
        return ResponseEntity.ok(riskDetectionService.getFlags(transactionReference));
    }
}
