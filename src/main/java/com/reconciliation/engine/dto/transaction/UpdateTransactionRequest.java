package com.reconciliation.engine.dto.transaction;

import com.reconciliation.engine.common.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PUT /api/v1/transactions/{id}}.
 *
 * Deliberately narrow: {@code transactionReference}, {@code account},
 * {@code amount}, {@code currency}, {@code transactionType}, and
 * {@code transactionTimestamp} are NOT here and cannot be changed once a
 * transaction exists — those are facts about what happened, and rewriting
 * them after the fact would corrupt financial history. Only two things are
 * genuinely mutable post-creation:
 *   - {@code status}: a transaction's processing outcome legitimately
 *     changes over time (PENDING -> COMPLETED, etc.). No state-machine
 *     validation is enforced yet (any valid enum value is accepted) — that
 *     belongs to a later phase once risk/reconciliation rules exist.
 *   - {@code externalReference}: an upstream processor reference can
 *     genuinely arrive or change after initial ingestion.
 */
public class UpdateTransactionRequest {

    @NotNull(message = "status is required")
    private TransactionStatus status;

    @Size(max = 128, message = "externalReference must be at most 128 characters")
    private String externalReference;

    protected UpdateTransactionRequest() {
        // required by Jackson
    }

    public UpdateTransactionRequest(TransactionStatus status, String externalReference) {
        this.status = status;
        this.externalReference = externalReference;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
