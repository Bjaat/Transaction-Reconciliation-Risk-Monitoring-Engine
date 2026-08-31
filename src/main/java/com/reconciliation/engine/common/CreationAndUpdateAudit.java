package com.reconciliation.engine.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

/**
 * Base class for entities whose state legitimately changes after creation
 * (Account status changes, Transaction status changes). Adds updated_at on
 * top of {@link CreationAudit}'s created_at.
 */
@MappedSuperclass
public abstract class CreationAndUpdateAudit extends CreationAudit {

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
