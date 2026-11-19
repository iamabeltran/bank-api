package com.portfolio.bankapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountRepository;
import com.portfolio.bankapi.error.IdempotencyConflictException;
import com.portfolio.bankapi.transaction.Transaction;
import com.portfolio.bankapi.transaction.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransferIdempotencyTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Mock
    AccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    TransferService service;
    Account from;
    Account to;

    @BeforeEach
    void setUp() {
        service = new TransferService(accounts, transactions);
        from = account(1L, "500");
        to = account(2L, "0");
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(from));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(to));
    }

    private Account account(long id, String balance) {
        Account account = new Account("Titular", "MXN");
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.deposit(amount);
        }
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private Transaction previousTransfer() {
        Transaction previous = Transaction.transfer(from, to, HUNDRED, "k1");
        ReflectionTestUtils.setField(previous, "id", 77L);
        return previous;
    }

    @Test
    void theFirstRequestWithAKeyIsExecutedAndStoresTheKey() {
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.transfer("k1", 1L, 2L, HUNDRED);

        assertThat(result.replayed()).isFalse();
        assertThat(result.transaction().getIdempotencyKey()).isEqualTo("k1");
        assertThat(from.getBalance()).isEqualByComparingTo("400");
        assertThat(to.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void aRepeatedRequestReturnsTheOriginalWithoutMovingMoneyAgain() {
        Transaction previous = previousTransfer();
        when(transactions.findByIdempotencyKey("k1")).thenReturn(Optional.of(previous));

        TransferResult result = service.transfer("k1", 1L, 2L, new BigDecimal("100"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.transaction()).isSameAs(previous);
        assertThat(from.getBalance()).isEqualByComparingTo("500");
        assertThat(to.getBalance()).isEqualByComparingTo("0");
        verify(transactions, never()).save(any());
    }

    @Test
    void reusingTheKeyWithADifferentAmountIsAConflict() {
        when(transactions.findByIdempotencyKey("k1")).thenReturn(Optional.of(previousTransfer()));

        assertThatThrownBy(() -> service.transfer("k1", 1L, 2L, new BigDecimal("200")))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(from.getBalance()).isEqualByComparingTo("500");
        verify(transactions, never()).save(any());
    }

    @Test
    void reusingTheKeyWithTheDirectionReversedIsAConflict() {
        when(transactions.findByIdempotencyKey("k1")).thenReturn(Optional.of(previousTransfer()));

        assertThatThrownBy(() -> service.transfer("k1", 2L, 1L, HUNDRED))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(transactions, never()).save(any());
    }

    @Test
    void aDuplicateKeyRejectedByTheDatabaseIsReportedAsAConflict() {
        when(transactions.save(any(Transaction.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.transfer("k1", 1L, 2L, HUNDRED))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void withoutAKeyTheIdempotencyTableIsNeverQueried() {
        when(transactions.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResult result = service.transfer(null, 1L, 2L, HUNDRED);

        assertThat(result.replayed()).isFalse();
        verify(transactions, never()).findByIdempotencyKey(any());
    }
}
