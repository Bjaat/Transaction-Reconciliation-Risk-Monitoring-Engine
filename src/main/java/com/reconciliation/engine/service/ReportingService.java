package com.reconciliation.engine.service;

import com.reconciliation.engine.dto.report.CurrencyAmountSummary;
import com.reconciliation.engine.dto.report.DimensionCount;
import com.reconciliation.engine.dto.report.ReconciliationReportResponse;
import com.reconciliation.engine.dto.report.RiskReportResponse;
import com.reconciliation.engine.dto.report.TransactionReportResponse;
import com.reconciliation.engine.exception.BadRequestException;
import com.reconciliation.engine.repository.ReconciliationLogRepository;
import com.reconciliation.engine.repository.RiskFlagRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import com.reconciliation.engine.repository.projection.DimensionCountProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportingService {

    private static final LocalDateTime UNBOUNDED_FROM = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime UNBOUNDED_TO =
            LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000);

    private final TransactionRepository transactionRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final RiskFlagRepository riskFlagRepository;

    public ReportingService(TransactionRepository transactionRepository,
                            ReconciliationLogRepository reconciliationLogRepository,
                            RiskFlagRepository riskFlagRepository) {
        this.transactionRepository = transactionRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.riskFlagRepository = riskFlagRepository;
    }

    public TransactionReportResponse transactions(LocalDateTime from, LocalDateTime to) {
        validateWindow(from, to);
        LocalDateTime queryFrom = queryFrom(from);
        LocalDateTime queryTo = queryTo(to);
        List<DimensionCount> byStatus = counts(transactionRepository.summarizeStatus(queryFrom, queryTo));
        List<DimensionCount> byType = counts(transactionRepository.summarizeType(queryFrom, queryTo));
        List<CurrencyAmountSummary> byCurrency = transactionRepository.summarizeAmountsByCurrency(queryFrom, queryTo)
                .stream()
                .map(row -> new CurrencyAmountSummary(
                        row.getCurrency(), row.getTransactionCount(), row.getTotalAmount()))
                .toList();
        return new TransactionReportResponse(from, to, total(byStatus), byCurrency, byStatus, byType);
    }

    public ReconciliationReportResponse reconciliations(LocalDateTime from, LocalDateTime to) {
        validateWindow(from, to);
        List<DimensionCount> byResult = counts(reconciliationLogRepository.summarizeResults(
                queryFrom(from), queryTo(to)));
        return new ReconciliationReportResponse(from, to, total(byResult), byResult);
    }

    public RiskReportResponse risks(LocalDateTime from, LocalDateTime to) {
        validateWindow(from, to);
        LocalDateTime queryFrom = queryFrom(from);
        LocalDateTime queryTo = queryTo(to);
        List<DimensionCount> byStatus = counts(riskFlagRepository.summarizeStatus(queryFrom, queryTo));
        List<DimensionCount> bySeverity = counts(riskFlagRepository.summarizeSeverity(queryFrom, queryTo));
        List<DimensionCount> byRule = counts(riskFlagRepository.summarizeRule(queryFrom, queryTo));
        return new RiskReportResponse(from, to, total(byStatus), byStatus, bySeverity, byRule);
    }

    private List<DimensionCount> counts(List<DimensionCountProjection> rows) {
        return rows.stream()
                .map(row -> new DimensionCount(String.valueOf(row.getGroupValue()), row.getTotal()))
                .toList();
    }

    private long total(List<DimensionCount> counts) {
        return counts.stream().mapToLong(DimensionCount::count).sum();
    }

    private void validateWindow(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must be before or equal to 'to'");
        }
    }

    private LocalDateTime queryFrom(LocalDateTime from) {
        return from == null ? UNBOUNDED_FROM : from;
    }

    private LocalDateTime queryTo(LocalDateTime to) {
        return to == null ? UNBOUNDED_TO : to;
    }
}
