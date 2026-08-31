-- V1__init_schema.sql
-- Initial schema for the Transaction Reconciliation & Risk Monitoring Engine.
--
-- Design notes (see README / conversation for full rationale):
--   * Every table has a BIGSERIAL surrogate "id" used only for internal FKs,
--     plus a separate business-facing reference column.
--   * All monetary columns are NUMERIC(19,4) — never FLOAT/DOUBLE.
--   * settlements and reconciliation_logs reference transactions by business
--     reference string (transaction_reference), NOT by foreign key, because
--     "no matching transaction" is a valid, expected state for those tables.
--   * risk_flags DOES use a real foreign key to transactions, since a risk
--     flag only ever exists for a transaction already in our system.

-- =========================================================================
-- accounts
-- =========================================================================
CREATE TABLE accounts (
    id                  BIGSERIAL PRIMARY KEY,
    account_number      VARCHAR(64)  NOT NULL,
    customer_reference  VARCHAR(64)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,

    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_accounts_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_accounts_customer_reference ON accounts (customer_reference);
CREATE INDEX idx_accounts_status ON accounts (status);

-- =========================================================================
-- transactions
-- =========================================================================
CREATE TABLE transactions (
    id                      BIGSERIAL PRIMARY KEY,
    transaction_reference   VARCHAR(64)     NOT NULL,
    account_id              BIGINT          NOT NULL,
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,
    transaction_type        VARCHAR(20)     NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    transaction_timestamp   TIMESTAMP       NOT NULL,
    external_reference      VARCHAR(128),
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,

    CONSTRAINT uk_transactions_reference UNIQUE (transaction_reference),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id)
        REFERENCES accounts (id),
    CONSTRAINT ck_transactions_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_type CHECK (transaction_type IN
        ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'PAYMENT', 'REFUND')),
    CONSTRAINT ck_transactions_status CHECK (status IN
        ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_transaction_timestamp ON transactions (transaction_timestamp);

-- =========================================================================
-- settlements
-- =========================================================================
CREATE TABLE settlements (
    id                              BIGSERIAL       PRIMARY KEY,
    transaction_reference           VARCHAR(64)     NOT NULL,
    external_settlement_reference   VARCHAR(64)     NOT NULL,
    amount                          NUMERIC(19, 4)  NOT NULL,
    currency                        VARCHAR(3)      NOT NULL,
    status                          VARCHAR(20)     NOT NULL,
    settlement_timestamp            TIMESTAMP       NOT NULL,
    created_at                      TIMESTAMP       NOT NULL,

    CONSTRAINT uk_settlements_external_reference UNIQUE (external_settlement_reference),
    CONSTRAINT ck_settlements_currency_len CHECK (char_length(currency) = 3),
    CONSTRAINT ck_settlements_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_settlements_status CHECK (status IN
        ('PENDING', 'SETTLED', 'FAILED', 'REVERSED'))

    -- Deliberately NO foreign key on transaction_reference — see file header.
);

CREATE INDEX idx_settlements_transaction_reference ON settlements (transaction_reference);
CREATE INDEX idx_settlements_status ON settlements (status);

-- =========================================================================
-- risk_flags
-- =========================================================================
CREATE TABLE risk_flags (
    id              BIGSERIAL       PRIMARY KEY,
    transaction_id  BIGINT          NOT NULL,
    rule_code       VARCHAR(64)     NOT NULL,
    severity        VARCHAR(20)     NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    detected_at     TIMESTAMP       NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMP       NOT NULL,

    CONSTRAINT fk_risk_flags_transaction FOREIGN KEY (transaction_id)
        REFERENCES transactions (id),
    CONSTRAINT ck_risk_flags_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_risk_flags_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED', 'FALSE_POSITIVE'))
);

CREATE INDEX idx_risk_flags_transaction_id ON risk_flags (transaction_id);
CREATE INDEX idx_risk_flags_severity ON risk_flags (severity);

-- Prevents two concurrently-OPEN flags for the same transaction/rule pair,
-- while still allowing historical resolved/dismissed flags for that same
-- pair to exist. This is a partial (conditional) unique index, which JPA's
-- @UniqueConstraint annotation cannot express — see RiskFlag entity Javadoc.
CREATE UNIQUE INDEX uk_risk_flags_open_transaction_rule
    ON risk_flags (transaction_id, rule_code)
    WHERE status = 'OPEN';

-- =========================================================================
-- reconciliation_logs
-- =========================================================================
CREATE TABLE reconciliation_logs (
    id                      BIGSERIAL       PRIMARY KEY,
    transaction_reference   VARCHAR(64)     NOT NULL,
    settlement_reference    VARCHAR(64),
    result                  VARCHAR(30)     NOT NULL,
    expected_amount         NUMERIC(19, 4),
    actual_amount           NUMERIC(19, 4),
    amount_difference       NUMERIC(19, 4),
    expected_status         VARCHAR(20),
    actual_status           VARCHAR(20),
    explanation             VARCHAR(1000),
    reconciled_at           TIMESTAMP       NOT NULL,
    created_at              TIMESTAMP       NOT NULL,

    CONSTRAINT ck_reconciliation_logs_result CHECK (result IN
        ('MATCHED', 'MISSING_SETTLEMENT', 'UNMATCHED_SETTLEMENT',
         'AMOUNT_MISMATCH', 'STATUS_MISMATCH', 'DUPLICATE_SETTLEMENT'))

    -- Deliberately NO foreign keys on transaction_reference / settlement_reference
    -- — see file header.
);

CREATE INDEX idx_reconciliation_logs_transaction_reference ON reconciliation_logs (transaction_reference);
CREATE INDEX idx_reconciliation_logs_settlement_reference ON reconciliation_logs (settlement_reference);
CREATE INDEX idx_reconciliation_logs_result ON reconciliation_logs (result);
