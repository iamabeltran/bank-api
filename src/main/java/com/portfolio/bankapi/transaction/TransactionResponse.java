package com.portfolio.bankapi.transaction;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        TransactionType type,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        Instant createdAt) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getFromAccountId(), tx.getToAccountId(),
                tx.getAmount(), tx.getCurrency(), tx.getStatus(), tx.getCreatedAt());
    }
}
