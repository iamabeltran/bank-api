package com.portfolio.bankapi.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.account.AccountService;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.InvalidPaginationException;
import com.portfolio.bankapi.support.IntegrationTest;
import com.portfolio.bankapi.transfer.TransferService;

/** Historial paginado contra MySQL real (consulta nativa con UNION ALL y los índices de V2). */
@IntegrationTest
class TransactionHistoryIntegrationTest {

    @Autowired
    AccountService accountService;

    @Autowired
    TransferService transferService;

    @Autowired
    TransactionHistoryService historyService;

    @Autowired
    MockMvc mvc;

    private BigDecimal money(int amount) {
        return BigDecimal.valueOf(amount);
    }

    /** Crea tres cuentas y movimientos en un orden conocido; devuelve la cuenta A. */
    private Account scenario() {
        Account a = accountService.create("A", "MXN");
        Account b = accountService.create("B", "MXN");
        Account c = accountService.create("C", "MXN");

        accountService.deposit(a.getId(), money(100));               // 1 (en A)
        accountService.deposit(a.getId(), money(101));               // 2 (en A)
        accountService.deposit(a.getId(), money(102));               // 3 (en A)
        accountService.deposit(b.getId(), money(500));               //   (NO en A)
        transferService.transfer(a.getId(), b.getId(), money(10));   // 4 (en A)
        transferService.transfer(a.getId(), b.getId(), money(20));   // 5 (en A)
        transferService.transfer(b.getId(), a.getId(), money(5));    // 6 (en A)
        transferService.transfer(b.getId(), c.getId(), money(7));    //   (NO en A)
        return a;
    }

    @Test
    void theHistoryHasOnlyThatAccountsMovementsNewestFirst() {
        Account a = scenario();

        PageResponse<TransactionResponse> first = historyService.history(a.getId(), 0, 4);
        PageResponse<TransactionResponse> second = historyService.history(a.getId(), 1, 4);
        PageResponse<TransactionResponse> beyond = historyService.history(a.getId(), 2, 4);

        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(2);
        // más reciente primero: 5 (B->A), 20 (A->B), 10 (A->B), 102 (depósito)
        assertThat(first.content()).extracting(t -> t.amount().intValue()).containsExactly(5, 20, 10, 102);
        assertThat(first.content().get(0).type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(second.content()).extracting(t -> t.amount().intValue()).containsExactly(101, 100);
        assertThat(beyond.content()).isEmpty();
    }

    @Test
    void anAccountWithoutMovementsReturnsAnEmptyPage() {
        Account empty = accountService.create("Vacía", "MXN");

        PageResponse<TransactionResponse> page = historyService.history(empty.getId(), 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void unknownAccountsAndInvalidPaginationAreRejected() {
        assertThatThrownBy(() -> historyService.history(999_999L, 0, 20)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> historyService.history(1L, 0, 101)).isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void theEndpointReturnsTheSamePageOverHttp() throws Exception {
        Account a = scenario();

        mvc.perform(get("/accounts/{id}/transactions", a.getId()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"));
    }
}
