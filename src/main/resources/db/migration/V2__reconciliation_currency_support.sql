-- V2__reconciliation_currency_support.sql
-- Phase 4 requires comparing settlement currency against transaction
-- currency and reporting both on a ReconciliationLog. Two genuine schema
-- gaps existed after V1: the result CHECK constraint didn't allow a
-- CURRENCY_MISMATCH value, and there were no columns to record which
-- currencies were actually compared. Both are minimal, additive changes —
-- no existing column or constraint is altered beyond widening the CHECK.

ALTER TABLE reconciliation_logs
    ADD COLUMN expected_currency VARCHAR(3),
    ADD COLUMN actual_currency VARCHAR(3);

ALTER TABLE reconciliation_logs
    DROP CONSTRAINT ck_reconciliation_logs_result;

ALTER TABLE reconciliation_logs
    ADD CONSTRAINT ck_reconciliation_logs_result CHECK (result IN
        ('MATCHED', 'MISSING_SETTLEMENT', 'UNMATCHED_SETTLEMENT',
         'AMOUNT_MISMATCH', 'STATUS_MISMATCH', 'DUPLICATE_SETTLEMENT',
         'CURRENCY_MISMATCH'));
