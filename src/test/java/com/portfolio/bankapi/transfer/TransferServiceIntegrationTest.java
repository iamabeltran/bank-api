package com.portfolio.bankapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountService;
import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.support.IntegrationTest;
import com.portfolio.bankapi.transaction.Transaction;

@IntegrationTest
class TransferServiceIntegrationTest {

    @Autowired
    AccountService accountService;

    @Autowired
    TransferService transferService;

    @Test
    void transferMovesMoneyAndTotalStaysTheSame() {
        Account ana = accountService.create("Ana", "MXN");
        Account luis = accountService.create("Luis", "MXN");
        accountService.deposit(ana.getId(), new BigDecimal("500"));

        Transaction tx = transferService.transfer(ana.getId(), luis.getId(), new BigDecimal("200.25"));

        BigDecimal anaBalance = accountService.get(ana.getId()).getBalance();
        BigDecimal luisBalance = accountService.get(luis.getId()).getBalance();
        assertThat(anaBalance).isEqualByComparingTo("299.75");
        assertThat(luisBalance).isEqualByComparingTo("200.25");
        assertThat(anaBalance.add(luisBalance)).isEqualByComparingTo("500");
        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getFromAccountId()).isEqualTo(ana.getId());
        assertThat(tx.getToAccountId()).isEqualTo(luis.getId());
    }

    @Test
    void failedTransferLeavesBothBalancesUntouched() {
        Account ana = accountService.create("Ana", "MXN");
        Account luis = accountService.create("Luis", "MXN");
        accountService.deposit(ana.getId(), new BigDecimal("100"));

        assertThatThrownBy(() -> transferService.transfer(ana.getId(), luis.getId(), new BigDecimal("150")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountService.get(ana.getId()).getBalance()).isEqualByComparingTo("100");
        assertThat(accountService.get(luis.getId()).getBalance()).isEqualByComparingTo("0");
    }
}
