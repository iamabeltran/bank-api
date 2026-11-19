package com.portfolio.bankapi.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.portfolio.bankapi.error.InvalidAmountException;

/** Test unitario puro: sin Spring, sin base de datos, corre en milisegundos. */
class AccountTest {

    @Test
    void newAccountStartsWithZeroBalance() {
        Account account = new Account("Ana", "MXN");

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void depositIncreasesBalance() {
        Account account = new Account("Ana", "MXN");

        account.deposit(new BigDecimal("100.50"));
        account.deposit(new BigDecimal("0.25"));

        assertThat(account.getBalance()).isEqualByComparingTo("100.75");
    }

    @Test
    void depositRejectsZeroNegativeAndNull() {
        Account account = new Account("Ana", "MXN");

        assertThatThrownBy(() -> account.deposit(BigDecimal.ZERO)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> account.deposit(new BigDecimal("-5"))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> account.deposit(null)).isInstanceOf(InvalidAmountException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }
}
