package com.reconciliation.engine.dto.risk;

import com.reconciliation.engine.common.RiskFlagStatus;
import com.reconciliation.engine.common.RiskSeverity;
import com.reconciliation.engine.entity.RiskFlag;

import java.time.LocalDateTime;

public class RiskFlagResponse {

    private final Long id;
    private final String transactionReference;
    private final String ruleCode;
    private final RiskSeverity severity;
    private final String description;
    private final LocalDateTime detectedAt;
    private final RiskFlagStatus status;

    public RiskFlagResponse(Long id, String transactionReference, String ruleCode, RiskSeverity severity,
                            String description, LocalDateTime detectedAt, RiskFlagStatus status) {
        this.id = id;
        this.transactionReference = transactionReference;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.description = description;
        this.detectedAt = detectedAt;
        this.status = status;
    }

    public static RiskFlagResponse fromEntity(RiskFlag flag) {
        return new RiskFlagResponse(flag.getId(), flag.getTransaction().getTransactionReference(),
                flag.getRuleCode(), flag.getSeverity(), flag.getDescription(), flag.getDetectedAt(), flag.getStatus());
    }

    public Long getId() { return id; }
    public String getTransactionReference() { return transactionReference; }
    public String getRuleCode() { return ruleCode; }
    public RiskSeverity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public RiskFlagStatus getStatus() { return status; }
}
