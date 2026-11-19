package com.portfolio.bankapi.error;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long id) {
        super("Account " + id + " not found");
    }
}
