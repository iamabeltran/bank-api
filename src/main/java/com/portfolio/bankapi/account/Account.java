package com.portfolio.bankapi.account;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.error.InvalidAmountException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_name", nullable = false, length = 120)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    // La columna es CHAR(3); sin esta anotación Hibernate esperaría VARCHAR y la validación fallaría.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // requerido por JPA
    }

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** La regla de negocio vive en la entidad: una cuenta nunca acepta montos inválidos. */
    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    /** Retira dinero; jamás deja la cuenta en negativo. */
    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }

    public Long getId() { return id; }
    public String getOwnerName() { return ownerName; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
