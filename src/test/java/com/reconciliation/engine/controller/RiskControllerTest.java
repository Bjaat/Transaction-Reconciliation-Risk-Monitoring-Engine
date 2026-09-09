package com.reconciliation.engine.controller;

import com.reconciliation.engine.dto.risk.BatchRiskEvaluationResult;
import com.reconciliation.engine.dto.risk.RiskEvaluationResult;
import com.reconciliation.engine.service.RiskDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskController.class)
class RiskControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RiskDetectionService riskDetectionService;

    @Test
    void evaluatesATransaction() throws Exception {
        when(riskDetectionService.evaluate("TX-1"))
                .thenReturn(new RiskEvaluationResult("TX-1", 2, List.of()));

        mockMvc.perform(post("/api/v1/risk/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"TX-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TX-1"))
                .andExpect(jsonPath("$.rulesEvaluated").value(2));
    }

    @Test
    void validatesTheEvaluationRequest() throws Exception {
        mockMvc.perform(post("/api/v1/risk/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionReference\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.transactionReference").exists());
    }

    @Test
    void returnsFlagHistory() throws Exception {
        when(riskDetectionService.getFlags("TX-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/risk/flags/TX-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void runsBatchEvaluation() throws Exception {
        when(riskDetectionService.evaluateAll()).thenReturn(new BatchRiskEvaluationResult(3, 2, 0));

        mockMvc.perform(post("/api/v1/risk/evaluations/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(3))
                .andExpect(jsonPath("$.flagsCreated").value(2));
    }
}
