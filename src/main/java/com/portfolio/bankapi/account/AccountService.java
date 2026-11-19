package com.portfolio.bankapi.account;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public Account create(String ownerName, String currency) {
        return accounts.save(new Account(ownerName, currency));
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Todo el método es UNA transacción: o se actualiza el saldo Y se registra el movimiento,
     * o no pasa nada. Eso es la "A" (atomicidad) de ACID.
     */
    @Transactional
    public Transaction deposit(Long accountId, BigDecimal amount) {
        Account account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.deposit(amount); // Hibernate guarda el cambio de saldo al hacer commit
        return transactions.save(Transaction.deposit(account, amount));
    }
}
