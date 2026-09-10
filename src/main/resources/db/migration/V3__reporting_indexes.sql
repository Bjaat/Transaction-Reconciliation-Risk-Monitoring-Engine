-- Phase 6 reporting filters reconciliation and risk history by these event
-- timestamps. Single-column indexes support every report grouping without
-- paying the write/storage cost of separate composite indexes for each
-- result, status, severity, and rule-code dimension.

CREATE INDEX idx_reconciliation_logs_reconciled_at
    ON reconciliation_logs (reconciled_at);

CREATE INDEX idx_risk_flags_detected_at
    ON risk_flags (detected_at);
