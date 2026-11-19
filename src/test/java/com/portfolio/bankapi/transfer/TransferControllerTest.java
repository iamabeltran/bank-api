package com.portfolio.bankapi.transfer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

import com.portfolio.bankapi.account.Account;
import com.portfolio.bankapi.error.AccountNotFoundException;
import com.portfolio.bankapi.error.IdempotencyConflictException;
import com.portfolio.bankapi.error.InsufficientFundsException;
import com.portfolio.bankapi.transaction.Transaction;

@WebMvcTest(TransferController.class)
class TransferControllerTest {

    private static final String VALID_BODY = "{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":200.00}";

    @Autowired
    MockMvc mvc;

    @MockBean
    TransferService service;

    private Account accountWithId(long id) {
        Account account = new Account("Titular", "MXN");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private Transaction transferTx() {
        Transaction tx = Transaction.transfer(accountWithId(1L), accountWithId(2L), new BigDecimal("200.00"));
        ReflectionTestUtils.setField(tx, "id", 9L);
        return tx;
    }

    @Test
    void validTransferReturns201() throws Exception {
        given(service.transfer(any(), eq(1L), eq(2L), any(BigDecimal.class)))
                .willReturn(new TransferResult(transferTx(), false));

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.fromAccountId").value(1))
                .andExpect(jsonPath("$.toAccountId").value(2));
    }

    @Test
    void missingFieldsReturn400() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fromAccountId").exists())
                .andExpect(jsonPath("$.errors.toAccountId").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void negativeAmountReturns400() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":-10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void insufficientFundsReturns422() throws Exception {
        given(service.transfer(any(), eq(1L), eq(2L), any(BigDecimal.class)))
                .willThrow(new InsufficientFundsException());

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Insufficient funds"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        given(service.transfer(any(), eq(1L), eq(2L), any(BigDecimal.class)))
                .willThrow(new AccountNotFoundException(2L));

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- idempotencia

    @Test
    void theIdempotencyKeyHeaderIsPassedToTheService() throws Exception {
        given(service.transfer(eq("key-1"), eq(1L), eq(2L), any(BigDecimal.class)))
                .willReturn(new TransferResult(transferTx(), false));

        mvc.perform(post("/transfers").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void aReplayedTransferReturns200WithTheReplayHeader() throws Exception {
        given(service.transfer(eq("key-1"), eq(1L), eq(2L), any(BigDecimal.class)))
                .willReturn(new TransferResult(transferTx(), true));

        mvc.perform(post("/transfers").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(9));
    }

    @Test
    void reusingAKeyWithADifferentRequestReturns422() throws Exception {
        given(service.transfer(eq("key-1"), eq(1L), eq(2L), any(BigDecimal.class)))
                .willThrow(new IdempotencyConflictException("key-1"));

        mvc.perform(post("/transfers").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Idempotency key conflict"));
    }

    @Test
    void aBlankKeyReturns400() throws Exception {
        mvc.perform(post("/transfers").header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Idempotency-Key"));
    }

    @Test
    void aKeyLongerThan64CharactersReturns400() throws Exception {
        mvc.perform(post("/transfers").header("Idempotency-Key", "k".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Idempotency-Key"));
    }
}
