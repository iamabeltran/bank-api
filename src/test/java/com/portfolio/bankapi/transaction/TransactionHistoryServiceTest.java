package com.portfolio.bankapi.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portfolio.bankapi.account.AccountRepository;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.InvalidPaginationException;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock
    AccountRepository accounts;

    @Mock
    TransactionRepository transactions;

    TransactionHistoryService service;

    @BeforeEach
    void setUp() {
        service = new TransactionHistoryService(accounts, transactions);
    }

    @Test
    void theOffsetAndTheWindowAreComputedFromPageAndSize() {
        when(accounts.existsById(1L)).thenReturn(true);
        // page 1 con size 20 -> offset 20 y window = offset + size = 40
        when(transactions.findHistoryPage(1L, 40L, 20, 20L)).thenReturn(List.of());
        when(transactions.countHistory(1L)).thenReturn(45L);

        PageResponse<TransactionResponse> page = service.history(1L, 1, 20);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(3);
        verify(transactions).findHistoryPage(1L, 40L, 20, 20L);
    }

    @Test
    void thePageMetadataIsReported() {
        when(accounts.existsById(1L)).thenReturn(true);
        when(transactions.findHistoryPage(1L, 30L, 10, 20L)).thenReturn(List.of());
        when(transactions.countHistory(1L)).thenReturn(25L);

        PageResponse<TransactionResponse> page = service.history(1L, 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content()).isEmpty();
    }

    @Test
    void anUnknownAccountIsNotFound() {
        when(accounts.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.history(99L, 0, 20)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void invalidPaginationIsRejectedBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.history(1L, -1, 20)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> service.history(1L, 0, 0)).isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> service.history(1L, 0, 101)).isInstanceOf(InvalidPaginationException.class);

        verify(accounts, never()).existsById(1L);
    }
}
