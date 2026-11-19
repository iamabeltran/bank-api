package com.portfolio.bankapi.transaction;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.portfolio.bankapi.account.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Un movimiento del libro contable. Los depósitos también se registran aquí. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "from_account")
    private Long fromAccountId; // null en depósitos

    @Column(name = "to_account", nullable = false)
    private Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // requerido por JPA
    }

    /** Fábrica con nombre: deja claro qué es cada movimiento. */
    public static Transaction deposit(Account account, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.type = TransactionType.DEPOSIT;
        tx.toAccountId = account.getId();
        tx.amount = amount;
        tx.currency = account.getCurrency();
        tx.status = TransactionStatus.COMPLETED;
        return tx;
    }

    public static Transaction transfer(Account from, Account to, BigDecimal amount) {
        return transfer(from, to, amount, null);
    }

    /** Transferencia con clave de idempotencia opcional (la restricción UNIQUE vive en la base de datos). */
    public static Transaction transfer(Account from, Account to, BigDecimal amount, String idempotencyKey) {
        Transaction tx = new Transaction();
        tx.type = TransactionType.TRANSFER;
        tx.fromAccountId = from.getId();
        tx.toAccountId = to.getId();
        tx.amount = amount;
        tx.currency = from.getCurrency();
        tx.idempotencyKey = idempotencyKey;
        tx.status = TransactionStatus.COMPLETED;
        return tx;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public TransactionType getType() { return type; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
