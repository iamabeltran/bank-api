package com.portfolio.bankapi.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.transaction.Transaction;

/** Prueba SOLO la capa web (HTTP, JSON, validaciones, errores). El servicio es un mock. */
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AccountService service;

    private Account accountWithId(long id) {
        Account account = new Account("Ana", "MXN");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void createAccountReturns201WithLocation() throws Exception {
        given(service.create("Ana", "MXN")).willReturn(accountWithId(1L));

        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Ana\",\"currency\":\"MXN\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ownerName").value("Ana"))
                .andExpect(jsonPath("$.currency").value("MXN"));
    }

    @Test
    void createAccountWithInvalidDataReturns400AndFieldErrors() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"\",\"currency\":\"mx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.ownerName").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void getMissingAccountReturns404() throws Exception {
        given(service.get(99L)).willThrow(new AccountNotFoundException(99L));

        mvc.perform(get("/accounts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void depositReturns201() throws Exception {
        Account account = accountWithId(1L);
        Transaction tx = Transaction.deposit(account, new BigDecimal("100.50"));
        ReflectionTestUtils.setField(tx, "id", 7L);
        given(service.deposit(eq(1L), any(BigDecimal.class))).willReturn(tx);

        mvc.perform(post("/accounts/1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void depositWithNegativeAmountReturns400() throws Exception {
        mvc.perform(post("/accounts/1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void depositWithoutAmountReturns400() throws Exception {
        mvc.perform(post("/accounts/1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }
}
