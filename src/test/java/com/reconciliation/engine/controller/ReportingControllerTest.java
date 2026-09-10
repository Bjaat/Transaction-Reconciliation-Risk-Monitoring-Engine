package com.reconciliation.engine.controller;

import com.reconciliation.engine.dto.report.ReconciliationReportResponse;
import com.reconciliation.engine.dto.report.RiskReportResponse;
import com.reconciliation.engine.dto.report.TransactionReportResponse;
import com.reconciliation.engine.exception.BadRequestException;
import com.reconciliation.engine.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
class ReportingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ReportingService reportingService;

    @Test
    void returnsTransactionSummaryWithBoundedWindow() throws Exception {
        LocalDateTime from = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-09-02T00:00:00");
        when(reportingService.transactions(from, to))
                .thenReturn(new TransactionReportResponse(from, to, 0, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports/transactions")
                        .queryParam("from", "2026-09-01T00:00:00Z")
                        .queryParam("to", "2026-09-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(0))
                .andExpect(jsonPath("$.amountsByCurrency").isArray())
                .andExpect(jsonPath("$.from").value("2026-09-01T00:00:00"));
    }

    @Test
    void equivalentOffsetsReachServiceAsTheSameUtcInstant() throws Exception {
        LocalDateTime instant = LocalDateTime.parse("2026-09-01T00:00:00");
        when(reportingService.transactions(instant, instant))
                .thenReturn(new TransactionReportResponse(
                        instant, instant, 0, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports/transactions")
                        .queryParam("from", "2026-09-01T05:30:00+05:30")
                        .queryParam("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01T00:00:00"))
                .andExpect(jsonPath("$.to").value("2026-09-01T00:00:00"));
    }

    @Test
    void differentOffsetsRetainTheirDifferentInstants() throws Exception {
        LocalDateTime fromUtc = LocalDateTime.parse("2026-08-31T18:30:00");
        LocalDateTime toUtc = LocalDateTime.parse("2026-09-01T00:00:00");
        when(reportingService.transactions(fromUtc, toUtc))
                .thenReturn(new TransactionReportResponse(
                        fromUtc, toUtc, 0, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports/transactions")
                        .queryParam("from", "2026-09-01T00:00:00+05:30")
                        .queryParam("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-31T18:30:00"))
                .andExpect(jsonPath("$.to").value("2026-09-01T00:00:00"));
    }

    @Test
    void returnsReconciliationSummary() throws Exception {
        when(reportingService.reconciliations(null, null))
                .thenReturn(new ReconciliationReportResponse(null, null, 0, List.of()));

        mockMvc.perform(get("/api/v1/reports/reconciliations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciliations").value(0))
                .andExpect(jsonPath("$.countsByResult").isArray());
    }

    @Test
    void returnsRiskSummary() throws Exception {
        when(reportingService.risks(null, null))
                .thenReturn(new RiskReportResponse(null, null, 0, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/reports/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFlags").value(0))
                .andExpect(jsonPath("$.countsByRule").isArray());
    }

    @Test
    void malformedTimestampReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/reports/transactions").queryParam("from", "not-a-timestamp"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from")));
    }

    @Test
    void reversedWindowReturnsBadRequest() throws Exception {
        LocalDateTime from = LocalDateTime.parse("2026-09-02T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-09-01T00:00:00");
        when(reportingService.transactions(from, to))
                .thenThrow(new BadRequestException("'from' must be before or equal to 'to'"));

        mockMvc.perform(get("/api/v1/reports/transactions")
                        .queryParam("from", "2026-09-02T00:00:00Z")
                        .queryParam("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("from")));
    }
}
