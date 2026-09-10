package com.reconciliation.engine.repository.projection;

/** A database-side grouped count used by the reporting service. */
public interface DimensionCountProjection {

    Object getGroupValue();

    long getTotal();
}
