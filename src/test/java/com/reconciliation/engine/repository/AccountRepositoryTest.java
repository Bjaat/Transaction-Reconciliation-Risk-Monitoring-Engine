package com.reconciliation.engine.repository;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.entity.Account;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence tests for {@link Account} / {@link AccountRepository}, run
 * against a real PostgreSQL container.
 *
 * The container is declared with {@code @Container @ServiceConnection}
 * directly on this class (matching Spring's own documented pattern) rather
 * than shared from a base class: {@code @ServiceConnection}'s context
 * customizer relies on the {@code @Testcontainers} JUnit extension to start
 * the container at the right point in the test lifecycle, which a
 * shared/manually-started "singleton container" across multiple test
 * classes does not reliably trigger.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AccountRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager entityManager;

    private Account newAccount(String accountNumber) {
        return new Account(accountNumber, "CUST-001", "USD", AccountStatus.ACTIVE);
    }

    @Test
    void savesAndReloadsAnAccountWithAllFields() {
        Account saved = accountRepository.saveAndFlush(newAccount("ACC-1001"));

        Account reloaded = accountRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAccountNumber()).isEqualTo("ACC-1001");
        assertThat(reloaded.getCustomerReference()).isEqualTo("CUST-001");
        assertThat(reloaded.getCurrency()).isEqualTo("USD");
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void enumStatusIsPersistedAsItsNameNotItsOrdinal() {
        accountRepository.saveAndFlush(newAccount("ACC-1002"));

        // Read the raw column value back with a native query to prove
        // @Enumerated(EnumType.STRING) is actually in effect — an ordinal
        // (0/1/2/3) would silently break the moment enum values are reordered.
        Object rawStatus = entityManager
                .createNativeQuery("SELECT status FROM accounts WHERE account_number = 'ACC-1002'")
                .getSingleResult();

        assertThat(rawStatus).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateAccountNumbers() {
        accountRepository.saveAndFlush(newAccount("ACC-DUP"));

        assertThatThrownBy(() -> accountRepository.saveAndFlush(newAccount("ACC-DUP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByAccountNumberReturnsTheMatchingAccount() {
        accountRepository.saveAndFlush(newAccount("ACC-1003"));

        Optional<Account> found = accountRepository.findByAccountNumber("ACC-1003");

        assertThat(found).isPresent();
        assertThat(found.get().getCustomerReference()).isEqualTo("CUST-001");
    }

    @Test
    void findByAccountNumberIsEmptyWhenNoMatch() {
        assertThat(accountRepository.findByAccountNumber("does-not-exist")).isEmpty();
    }
}
