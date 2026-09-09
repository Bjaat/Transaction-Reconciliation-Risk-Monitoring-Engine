package com.reconciliation.engine.dto.risk;

public record BatchRiskEvaluationResult(int totalProcessed, int flagsCreated, int failed) {
}
