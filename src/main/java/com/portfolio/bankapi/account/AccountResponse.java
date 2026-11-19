package com.portfolio.bankapi.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String ownerName,
        BigDecimal balance,
        String currency,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(),
                account.getBalance(), account.getCurrency(), account.getCreatedAt());
    }
}
