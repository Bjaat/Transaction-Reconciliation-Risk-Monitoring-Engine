package com.reconciliation.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconciliation.engine.common.ReconciliationStatus;
import com.reconciliation.engine.dto.reconciliation.BatchReconciliationResult;
import com.reconciliation.engine.dto.reconciliation.ReconcileTransactionRequest;
import com.reconciliation.engine.dto.reconciliation.ReconciliationResult;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReconciliationService reconciliationService;

    private ReconciliationResult sampleResult(ReconciliationStatus status) {
        return new ReconciliationResult(1L, "TXN-1", "EXT-1", status,
                BigDecimal.TEN, BigDecimal.TEN, "USD", "USD",
                "COMPLETED", "SETTLED", "example", LocalDateTime.now());
    }

    @Test
    void reconcileReturns201() throws Exception {
        when(reconciliationService.reconcile("TXN-1")).thenReturn(sampleResult(ReconciliationStatus.MATCHED));

        mockMvc.perform(post("/api/v1/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReconcileTransactionRequest("TXN-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"));
    }

    @Test
    void reconcileWithBlankReferenceReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReconcileTransactionRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.transactionReference").exists());
    }

    @Test
    void reconcileForUnknownTransactionReturns404() throws Exception {
        when(reconciliationService.reconcile("MISSING"))
                .thenThrow(new ResourceNotFoundException("Transaction not found: MISSING"));

        mockMvc.perform(post("/api/v1/reconciliations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReconcileTransactionRequest("MISSING"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyReturns200WithOrderedResults() throws Exception {
        when(reconciliationService.getHistory("TXN-1")).thenReturn(
                List.of(sampleResult(ReconciliationStatus.MATCHED), sampleResult(ReconciliationStatus.MISSING_SETTLEMENT)));

        mockMvc.perform(get("/api/v1/reconciliations/TXN-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("MATCHED"))
                .andExpect(jsonPath("$[1].status").value("MISSING_SETTLEMENT"));
    }

    @Test
    void historyForUnknownTransactionReturns404() throws Exception {
        when(reconciliationService.getHistory(anyString()))
                .thenThrow(new ResourceNotFoundException("Transaction not found: MISSING"));

        mockMvc.perform(get("/api/v1/reconciliations/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchReturns200WithStatistics() throws Exception {
        when(reconciliationService.reconcileAll()).thenReturn(
                new BatchReconciliationResult(10, 6, 1, 1, 1, 1, 0, 0));

        mockMvc.perform(post("/api/v1/reconciliations/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(10))
                .andExpect(jsonPath("$.matched").value(6));
    }
}
