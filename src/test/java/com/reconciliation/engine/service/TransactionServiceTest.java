package com.reconciliation.engine.service;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.TransactionStatus;
import com.reconciliation.engine.common.TransactionType;
import com.reconciliation.engine.dto.transaction.CreateTransactionRequest;
import com.reconciliation.engine.dto.transaction.TransactionFilter;
import com.reconciliation.engine.dto.transaction.TransactionResponse;
import com.reconciliation.engine.dto.transaction.UpdateTransactionRequest;
import com.reconciliation.engine.entity.Account;
import com.reconciliation.engine.entity.Transaction;
import com.reconciliation.engine.exception.ResourceNotFoundException;
import com.reconciliation.engine.repository.AccountRepository;
import com.reconciliation.engine.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, accountRepository);
    }

    private Account account(String accountNumber) {
        return new Account(accountNumber, "CUST-1", "USD", AccountStatus.ACTIVE);
    }

    // ---- create ----

    @Test
    void createSavesTransactionForExistingAccountAsPending() {
        Account account = account("ACC-1");
        when(accountRepository.findByAccountNumber("ACC-1")).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1", "ACC-1", new BigDecimal("100.0000"), "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), "EXT-1");

        TransactionResponse response = service.create(request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.getTransactionReference()).isEqualTo("TXN-1");
        assertThat(response.getAccountNumber()).isEqualTo("ACC-1");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void createThrowsNotFoundWhenAccountDoesNotExist() {
        when(accountRepository.findByAccountNumber("MISSING")).thenReturn(Optional.empty());

        CreateTransactionRequest request = new CreateTransactionRequest(
                "TXN-1", "MISSING", BigDecimal.TEN, "USD",
                TransactionType.PAYMENT, LocalDateTime.now(), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MISSING");

        verify(transactionRepository, never()).save(any());
    }

    // ---- getById ----

    @Test
    void getByIdReturnsTransactionWhenFound() {
        Transaction transaction = new Transaction("TXN-2", account("ACC-2"), BigDecimal.TEN, "USD",
                TransactionType.DEPOSIT, TransactionStatus.COMPLETED, LocalDateTime.now(), null);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(transaction));

        TransactionResponse response = service.getById(5L);

        assertThat(response.getTransactionReference()).isEqualTo("TXN-2");
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---- update ----

    @Test
    void updateChangesStatusAndExternalReference() {
        Transaction transaction = new Transaction("TXN-3", account("ACC-3"), BigDecimal.TEN, "USD",
                TransactionType.PAYMENT, TransactionStatus.PENDING, LocalDateTime.now(), null);
        when(transactionRepository.findById(7L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionRequest request = new UpdateTransactionRequest(TransactionStatus.COMPLETED, "NEW-EXT-REF");

        TransactionResponse response = service.update(7L, request);

        assertThat(response.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.getExternalReference()).isEqualTo("NEW-EXT-REF");
    }

    @Test
    void updateThrowsNotFoundWhenTransactionMissing() {
        when(transactionRepository.findById(123L)).thenReturn(Optional.empty());
        UpdateTransactionRequest request = new UpdateTransactionRequest(TransactionStatus.FAILED, null);

        assertThatThrownBy(() -> service.update(123L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void updateDoesNotAllowChangingAmountAccountOrType() {
        // UpdateTransactionRequest structurally has no amount/account/type
        // fields at all, so this is really a compile-time guarantee — this
        // test documents that guarantee and confirms the original
        // immutable fields on the entity are untouched by an update call.
        Account originalAccount = account("ACC-4");
        Transaction transaction = new Transaction("TXN-4", originalAccount, new BigDecimal("50.0000"), "EUR",
                TransactionType.REFUND, TransactionStatus.PENDING, LocalDateTime.now(), null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResponse response = service.update(1L, new UpdateTransactionRequest(TransactionStatus.COMPLETED, null));

        assertThat(response.getAmount()).isEqualByComparingTo("50.0000");
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getTransactionType()).isEqualTo(TransactionType.REFUND);
        assertThat(response.getAccountNumber()).isEqualTo("ACC-4");
    }

    // ---- list/filter ----

    @Test
    void listDelegatesPagingToRepository() {
        Transaction transaction = new Transaction("TXN-5", account("ACC-5"), BigDecimal.TEN, "USD",
                TransactionType.PAYMENT, TransactionStatus.COMPLETED, LocalDateTime.now(), null);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Transaction> page = new PageImpl<>(List.of(transaction), pageable, 1);
        when(transactionRepository.findAll(
                ArgumentMatchers.<Specification<Transaction>>isNull(), eq(pageable))).thenReturn(page);

        TransactionFilter filter = new TransactionFilter(null, null, null, null, null, null, null, null);
        Page<TransactionResponse> result = service.list(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getTransactionReference()).isEqualTo("TXN-5");
    }
}
