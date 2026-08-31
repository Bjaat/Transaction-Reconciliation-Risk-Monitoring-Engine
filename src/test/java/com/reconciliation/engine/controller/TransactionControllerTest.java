package com.reconciliation.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.transaction.CreateTransactionRequest;
import com.reconciliation.engine.dto.transaction.TransactionResponse;
import com.reconciliation.engine.dto.transaction.UpdateTransactionRequest;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    private TransactionResponse sampleResponse() {
        return new TransactionResponse(1L, "TXN-1", "ACC-1", new BigDecimal("100.0000"), "USD",
                TransactionType.PAYMENT, TransactionStatus.PENDING, LocalDateTime.now(), "EXT-1",
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createReturns201WithBody() throws Exception {
        when(transactionService.create(any())).thenReturn(sampleResponse());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1", "ACC-1", new BigDecimal("100.00"), "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), "EXT-1");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionReference").value("TXN-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithInvalidAmountReturns400WithFieldErrors() throws Exception {
        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1", "ACC-1", new BigDecimal("-5.00"), "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), null);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void createWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        when(transactionService.getById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.transactionReference").value("TXN-1"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(transactionService.getById(99L)).thenThrow(new ResourceNotFoundException("Transaction not found: 99"));

        mockMvc.perform(get("/api/v1/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Transaction not found: 99"));
    }

    @Test
    void listReturnsPagedResponse() throws Exception {
        Page<TransactionResponse> page = new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1);
        when(transactionService.list(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference").value("TXN-1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void listWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/transactions?status=NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("status")));
    }

    @Test
    void updateReturns200WithUpdatedBody() throws Exception {
        TransactionResponse updated = sampleResponse();
        when(transactionService.update(eq(1L), any())).thenReturn(updated);

        UpdateTransactionRequest request = new UpdateTransactionRequest(TransactionStatus.COMPLETED, "NEW-REF");

        mockMvc.perform(put("/api/v1/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithMissingStatusReturns400() throws Exception {
        String bodyMissingStatus = "{\"externalReference\":\"REF-1\"}";

        mockMvc.perform(put("/api/v1/transactions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingStatus))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }
}
