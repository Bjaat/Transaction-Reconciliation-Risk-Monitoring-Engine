package com.reconciliation.engine.entity;

import com.reconciliation.engine.common.AccountStatus;
import com.reconciliation.engine.common.CreationAndUpdateAudit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

/**
 * A financial account that owns zero or more transactions.
 *
 * Deliberately does NOT hold a {@code List<Transaction>} collection back to
 * its transactions. An account can accumulate an unbounded number of
 * transactions over its lifetime; loading them as an entity collection
 * invites N+1 queries and unbounded memory use. Transactions for an account
 * are fetched explicitly via {@code TransactionRepository.findByAccountId(...)}
 * with pagination, in whichever later phase needs them.
 */
@Entity
@Table(
        name = "accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_accounts_account_number", columnNames = "account_number")
        }
)
public class Account extends CreationAndUpdateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Business-facing account identifier (e.g. an account number issued to a
     * customer). Distinct from {@link #id}, which is purely an internal
     * surrogate key used for foreign keys/joins.
     */
    @Column(name = "account_number", nullable = false, updatable = false, length = 64)
    private String accountNumber;

    /**
     * Identifier of the customer/owner this account belongs to. Kept as a
     * plain reference (not a foreign key) since customer management is out
     * of scope for this system — we simulate transaction processing, not a
     * full customer/CRM domain.
     */
    @Column(name = "customer_reference", nullable = false, length = 64)
    private String customerReference;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    protected Account() {
        // required by JPA
    }

    public Account(String accountNumber, String customerReference, String currency, AccountStatus status) {
        this.accountNumber = accountNumber;
        this.customerReference = customerReference;
        this.currency = currency;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    /**
     * Equality is based on the business key ({@code accountNumber}), not the
     * generated id. This makes equality meaningful even for a transient
     * (not-yet-persisted) instance, and stable across the object's lifetime —
     * unlike {@code id}, which is null until the first save.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(accountNumber, account.accountNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber);
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", accountNumber='" + accountNumber + '\'' +
                ", customerReference='" + customerReference + '\'' +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                '}';
    }
}
