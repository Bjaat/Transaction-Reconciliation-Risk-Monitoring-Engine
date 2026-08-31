package com.reconciliation.engine.service;

import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.dto.transaction.CreateTransactionRequest;
import com.reconciliation.engine.dto.transaction.TransactionFilter;
import com.reconciliation.engine.dto.transaction.TransactionResponse;
import com.reconciliation.engine.dto.transaction.UpdateTransactionRequest;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.AccountRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import com.reconciliation.engine.repository.TransactionSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Application/business logic for transactions. Controllers stay thin and
 * delegate everything here: entity/DTO conversion, not-found handling,
 * account resolution, and dynamic filtering.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + request.getAccountNumber()));

        Transaction transaction = new Transaction(
                request.getTransactionReference(),
                account,
                request.getAmount(),
                request.getCurrency(),
                request.getTransactionType(),
                TransactionStatus.PENDING,
                request.getTransactionTimestamp(),
                request.getExternalReference()
        );

        Transaction saved = transactionRepository.save(transaction);
        log.info("Created transaction {} (reference={}) for account {}",
                saved.getId(), saved.getTransactionReference(), account.getAccountNumber());
        return TransactionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(Long id) {
        return TransactionResponse.fromEntity(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(TransactionFilter filter, Pageable pageable) {
        Specification<Transaction> specification = buildSpecification(filter);
        return transactionRepository.findAll(specification, pageable).map(TransactionResponse::fromEntity);
    }

    @Transactional
    public TransactionResponse update(Long id, UpdateTransactionRequest request) {
        Transaction transaction = findEntityById(id);
        transaction.setStatus(request.getStatus());
        // externalReference has no dedicated setter in the entity — a
        // deliberate Phase 2 design choice for a field that was originally
        // treated as set-once-at-ingestion. Phase 3 needs it mutable (an
        // upstream reference can genuinely arrive later), which is a real,
        // narrow requirement change, so a setter is added to the entity
        // rather than worked around here.
        transaction.setExternalReference(request.getExternalReference());

        Transaction saved = transactionRepository.save(transaction);
        log.info("Updated transaction {} -> status={}", saved.getId(), saved.getStatus());
        return TransactionResponse.fromEntity(saved);
    }

    private Transaction findEntityById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    /**
     * Combines only the filters that were actually supplied. Built here
     * (not relying on {@code Specification.and}'s null-handling) so the
     * combination logic is explicit and doesn't depend on a particular
     * Spring Data version's behavior.
     */
    private Specification<Transaction> buildSpecification(TransactionFilter filter) {
        List<Specification<Transaction>> specs = new ArrayList<>();
        addIfPresent(specs, TransactionSpecifications.hasAccountNumber(filter.getAccountNumber()));
        addIfPresent(specs, TransactionSpecifications.hasStatus(filter.getStatus()));
        addIfPresent(specs, TransactionSpecifications.hasTransactionType(filter.getTransactionType()));
        addIfPresent(specs, TransactionSpecifications.hasCurrency(filter.getCurrency()));
        addIfPresent(specs, TransactionSpecifications.timestampFrom(filter.getFrom()));
        addIfPresent(specs, TransactionSpecifications.timestampTo(filter.getTo()));
        addIfPresent(specs, TransactionSpecifications.amountAtLeast(filter.getMinAmount()));
        addIfPresent(specs, TransactionSpecifications.amountAtMost(filter.getMaxAmount()));

        Specification<Transaction> combined = null;
        for (Specification<Transaction> spec : specs) {
            combined = (combined == null) ? spec : combined.and(spec);
        }
        return combined;
    }

    private void addIfPresent(List<Specification<Transaction>> specs, Specification<Transaction> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }
}
