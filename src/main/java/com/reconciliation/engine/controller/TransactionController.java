package com.reconciliation.engine.controller;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.PagedResponse;
import com.reconciliation.engine.dto.transaction.CreateTransactionRequest;
import com.reconciliation.engine.dto.transaction.TransactionFilter;
import com.reconciliation.engine.dto.transaction.TransactionResponse;
import com.reconciliation.engine.dto.transaction.UpdateTransactionRequest;
import com.reconciliation.engine.exception.BadRequestException;
import com.reconciliation.engine.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * REST API for transactions. Deliberately thin: binds/validates the HTTP
 * request, delegates to {@link TransactionService}, and picks the HTTP
 * status code. No business logic lives here.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TransactionResponse>> list(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @PageableDefault(size = 20) Pageable pageable) {

        TransactionFilter filter = new TransactionFilter(
                accountId,
                parseEnum(TransactionStatus.class, "status", status),
                parseEnum(TransactionType.class, "transactionType", transactionType),
                currency,
                from == null ? null : from.toLocalDateTime(),
                to == null ? null : to.toLocalDateTime(),
                minAmount,
                maxAmount
        );

        Page<TransactionResponse> page = transactionService.list(filter, pageable);
        return ResponseEntity.ok(PagedResponse.from(page));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateTransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request));
    }

    /**
     * Parses an optional enum query parameter, producing a clear
     * {@link BadRequestException} (-> 400, via {@code GlobalExceptionHandler})
     * naming the actual invalid value and the parameter it came from,
     * rather than a generic Spring type-conversion error.
     */
    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String paramName, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, rawValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid value for '" + paramName + "': " + rawValue);
        }
    }
}
