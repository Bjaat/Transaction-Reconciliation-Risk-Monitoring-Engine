package com.reconciliation.engine.controller;

import com.reconciliation.engine.dto.reconciliation.BatchReconciliationResult;
import com.reconciliation.engine.dto.reconciliation.ReconcileTransactionRequest;
import com.reconciliation.engine.dto.reconciliation.ReconciliationResult;
import com.reconciliation.engine.service.ReconciliationService;
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

/**
 * REST API for reconciliation. Thin, per the project's established
 * convention — see {@code TransactionController}.
 */
@RestController
@RequestMapping("/api/v1/reconciliations")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** Reconciles one transaction and persists a new ReconciliationLog entry. */
    @PostMapping
    public ResponseEntity<ReconciliationResult> reconcile(@Valid @RequestBody ReconcileTransactionRequest request) {
        ReconciliationResult result = reconciliationService.reconcile(request.getTransactionReference());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /** Full reconciliation history for a transaction, newest first. */
    @GetMapping("/{transactionReference}")
    public ResponseEntity<List<ReconciliationResult>> getHistory(@PathVariable String transactionReference) {
        return ResponseEntity.ok(reconciliationService.getHistory(transactionReference));
    }

    /** Reconciles every transaction in the system; one failure doesn't block the rest. */
    @PostMapping("/run")
    public ResponseEntity<BatchReconciliationResult> runBatch() {
        return ResponseEntity.ok(reconciliationService.reconcileAll());
    }
}
