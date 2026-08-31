package com.reconciliation.engine.common;

/**
 * Lifecycle status of an {@link com.reconciliation.engine.entity.Account}.
 *
 * ACTIVE            - account can transact normally.
 * INACTIVE          - dormant, no transactions expected but not formally closed.
 * SUSPENDED         - temporarily blocked (e.g. under investigation); transactions
 *                     should be rejected while in this state.
 * CLOSED            - permanently closed; retained for history/audit only.
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    CLOSED
}
