package com.reconciliation.engine.dto.reconciliation;

/** Aggregated statistics returned by {@code POST /api/v1/reconciliations/run}. */
public class BatchReconciliationResult {

    private final int totalProcessed;
    private final int matched;
    private final int amountMismatch;
    private final int currencyMismatch;
    private final int statusMismatch;
    private final int missingSettlement;
    private final int duplicateSettlement;
    private final int failed;

    public BatchReconciliationResult(int totalProcessed, int matched, int amountMismatch, int currencyMismatch,
                                      int statusMismatch, int missingSettlement, int duplicateSettlement, int failed) {
        this.totalProcessed = totalProcessed;
        this.matched = matched;
        this.amountMismatch = amountMismatch;
        this.currencyMismatch = currencyMismatch;
        this.statusMismatch = statusMismatch;
        this.missingSettlement = missingSettlement;
        this.duplicateSettlement = duplicateSettlement;
        this.failed = failed;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public int getMatched() {
        return matched;
    }

    public int getAmountMismatch() {
        return amountMismatch;
    }

    public int getCurrencyMismatch() {
        return currencyMismatch;
    }

    public int getStatusMismatch() {
        return statusMismatch;
    }

    public int getMissingSettlement() {
        return missingSettlement;
    }

    public int getDuplicateSettlement() {
        return duplicateSettlement;
    }

    public int getFailed() {
        return failed;
    }
}
