package com.reconciliation.engine.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;

/**
 * Base class for entities that are effectively immutable, append-only records
 * (Settlement, RiskFlag, ReconciliationLog): they get a created_at timestamp
 * but no updated_at, because nothing in the current design mutates them after
 * insert. If a future phase introduces an update workflow for one of these
 * (e.g. resolving a RiskFlag), that entity can extend
 * {@link CreationAndUpdateAudit} instead.
 */
@MappedSuperclass
public abstract class CreationAudit {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
