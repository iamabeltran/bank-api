package com.portfolio.bankapi.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;
import com.portfolio.bankapi.transaction.TransactionStatus;
import com.portfolio.bankapi.transaction.TransactionType;

/** Prueba el servicio con repositorios "falsos" (Mockito): sin base de datos. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    AccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    @InjectMocks
    AccountService service;

    @Test
    void depositUpdatesBalanceAndRecordsTransaction() {
        Account account = new Account("Ana", "MXN");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.deposit(1L, new BigDecimal("250.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("250.00");
        assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(tx.getAmount()).isEqualByComparingTo("250.00");
        assertThat(tx.getCurrency()).isEqualTo("MXN");
    }

    @Test
    void depositToMissingAccountThrowsAndRecordsNothing() {
        when(accounts.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit(99L, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);

        verify(transactions, never()).save(any());
    }

    @Test
    void getMissingAccountThrows() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(AccountNotFoundException.class);
    }
}
