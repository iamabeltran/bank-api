package com.portfolio.bankapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountRepository;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.error.InvalidTransferException;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;
import com.portfolio.bankapi.transaction.TransactionStatus;
import com.portfolio.bankapi.transaction.TransactionType;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    AccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    @InjectMocks
    TransferService service;

    private Account account(String currency, String balance) {
        Account account = new Account("Titular", currency);
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.deposit(amount);
        }
        return account;
    }

    @Test
    void transferMovesMoneyAndRecordsTransaction() {
        Account from = account("MXN", "500");
        Account to = account("MXN", "100");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(from));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(to));
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.transfer(1L, 2L, new BigDecimal("200.00"));

        assertThat(from.getBalance()).isEqualByComparingTo("300");
        assertThat(to.getBalance()).isEqualByComparingTo("300");
        assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void locksAccountsInAscendingIdOrderEvenWhenSourceHasHigherId() {
        Account from = account("MXN", "500"); // id 5
        Account to = account("MXN", "0");     // id 2
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(to));
        when(accounts.findByIdForUpdate(5L)).thenReturn(Optional.of(from));
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(5L, 2L, BigDecimal.TEN);

        InOrder order = inOrder(accounts);
        order.verify(accounts).findByIdForUpdate(2L);
        order.verify(accounts).findByIdForUpdate(5L);
        assertThat(from.getBalance()).isEqualByComparingTo("490");
        assertThat(to.getBalance()).isEqualByComparingTo("10");
    }

    @Test
    void insufficientFundsChangesNothingAndRecordsNothing() {
        Account from = account("MXN", "50");
        Account to = account("MXN", "100");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(from));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("100")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("50");
        assertThat(to.getBalance()).isEqualByComparingTo("100");
        verify(transactions, never()).save(any());
    }

    @Test
    void sameAccountIsRejectedBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.transfer(1L, 1L, BigDecimal.TEN))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(accounts, transactions);
    }

    @Test
    void differentCurrenciesAreRejected() {
        Account from = account("MXN", "500");
        Account to = account("USD", "0");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(from));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.transfer(1L, 2L, BigDecimal.TEN))
                .isInstanceOf(InvalidTransferException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("500");
        verify(transactions, never()).save(any());
    }

    @Test
    void missingAccountThrowsNotFound() {
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(1L, 2L, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);

        verify(transactions, never()).save(any());
    }
}
