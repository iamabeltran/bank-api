package com.portfolio.bankapi.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.portfolio.bankapi.support.IntegrationTest;
import com.portfolio.bankapi.transaction.TransactionRepository;

/**
 * Verifica contra MySQL real que los mapeos JPA coinciden con el esquema de Flyway
 * (ddl-auto=validate) y que el depósito persiste saldo + movimiento.
 */
@IntegrationTest
class AccountServiceIntegrationTest {

    @Autowired
    AccountService service;

    @Autowired
    TransactionRepository transactions;

    @Test
    void depositsAccumulateAndAreRecorded() {
        Account account = service.create("Ana", "MXN");

        service.deposit(account.getId(), new BigDecimal("100.50"));
        service.deposit(account.getId(), new BigDecimal("0.25"));

        Account reloaded = service.get(account.getId());
        assertThat(reloaded.getBalance()).isEqualByComparingTo("100.75");
        assertThat(reloaded.getVersion()).isEqualTo(2L);
        assertThat(transactions.findAll())
                .filteredOn(tx -> account.getId().equals(tx.getToAccountId()))
                .hasSize(2);
    }
}
