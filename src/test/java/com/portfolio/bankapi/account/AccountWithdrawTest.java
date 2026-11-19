package com.portfolio.bankapi.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.error.InvalidAmountException;

class AccountWithdrawTest {

    private Account accountWith(String balance) {
        Account account = new Account("Ana", "MXN");
        account.deposit(new BigDecimal(balance));
        return account;
    }

    @Test
    void withdrawDecreasesBalance() {
        Account account = accountWith("100");

        account.withdraw(new BigDecimal("30.50"));

        assertThat(account.getBalance()).isEqualByComparingTo("69.50");
    }

    @Test
    void withdrawingTheExactBalanceIsAllowed() {
        Account account = accountWith("100");

        account.withdraw(new BigDecimal("100"));

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawMoreThanBalanceFailsAndKeepsBalance() {
        Account account = accountWith("100");

        assertThatThrownBy(() -> account.withdraw(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void withdrawRejectsZeroAndNegative() {
        Account account = accountWith("100");

        assertThatThrownBy(() -> account.withdraw(BigDecimal.ZERO)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> account.withdraw(new BigDecimal("-1"))).isInstanceOf(InvalidAmountException.class);
    }
}
